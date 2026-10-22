package com.belleza.notificaciones.evento;

import com.belleza.notificaciones.error.ErrorPermanente;
import com.belleza.notificaciones.error.ErrorTransitorio;
import com.belleza.notificaciones.error.SimuladorFallos;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Flujo común de los consumidores de citas con ACK manual (guías 2.2.1 a 2.2.3).
 *
 * Tabla de decisiones (el consumidor SIEMPRE responde con ACK o NACK; nunca deja el mensaje colgado):
 *
 * | Situación                          | Qué se hace                                  | Resultado         |
 * |------------------------------------|----------------------------------------------|-------------------|
 * | eventoId ya procesado en esta cola | no se reprocesa                              | basicAck          |
 * | procesado sin error                | se marca el eventoId                         | basicAck          |
 * | ErrorTransitorio (recuperable)     | reintento con backoff 1 s, 2 s, 4 s          | si se recupera: basicAck |
 * |   ... y se agotan los reintentos   | log.warn                                     | basicNack → DLQ   |
 * | ErrorPermanente / datos inválidos  | log.warn, sin reintentar                     | basicNack → DLQ   |
 * | error inesperado (bug)             | log.error, se trata como permanente          | basicNack → DLQ   |
 * | hilo interrumpido (apagado)        | no es culpa del mensaje                      | basicNack reencolando |
 *
 * basicNack(tag, false, false): multiple=false (solo este mensaje), requeue=false. Sin reencolar,
 * RabbitMQ lo manda al x-dead-letter-exchange de la cola: el mensaje no se pierde, queda en la
 * DLQ con el header x-death (motivo "rejected").
 *
 * Por qué no reencolar (requeue=true) ante un error: el mensaje volvería al frente de la cola y
 * fallaría de nuevo al instante, en un bucle infinito que bloquea a los demás (prefetch 1).
 */
@Component
public class ConsumoConAck {

    private static final Logger log = LoggerFactory.getLogger(ConsumoConAck.class);

    private final RegistroEventosProcesados registro;
    private final SimuladorFallos simulador;
    private final int maxReintentos;
    private final long intervaloInicialMs;
    private final double multiplicador;

    public ConsumoConAck(RegistroEventosProcesados registro, SimuladorFallos simulador,
            @Value("${salon.mensajeria.reintentos.max-reintentos}") int maxReintentos,
            @Value("${salon.mensajeria.reintentos.intervalo-inicial-ms}") long intervaloInicialMs,
            @Value("${salon.mensajeria.reintentos.multiplicador}") double multiplicador) {
        this.registro = registro;
        this.simulador = simulador;
        this.maxReintentos = maxReintentos;
        this.intervaloInicialMs = intervaloInicialMs;
        this.multiplicador = multiplicador;
    }

    public void procesar(String cola, CitaEvento evento, String simularFallo, Channel channel, long tag,
            Consumer<CitaEvento> accion) throws IOException {
        // 1. Idempotencia: at-least-once puede entregar dos veces el mismo evento
        if (evento.eventoId() != null && registro.yaProcesado(cola, evento.eventoId())) {
            log.info("[{}] duplicado ignorado eventoId={} cita={}", cola, evento.eventoId(), evento.citaId());
            channel.basicAck(tag, false);
            return;
        }

        try {
            validar(evento);
            ejecutarConReintentos(cola, evento, simularFallo, accion);
            registro.marcarProcesado(cola, evento.eventoId());
            channel.basicAck(tag, false);

        } catch (ErrorTransitorio e) {
            // Solo llega aquí si ya se agotaron los reintentos
            log.warn("[{}] agotados {} reintentos eventoId={} cita={}: {} -> NACK sin reencolar, va a la DLQ",
                    cola, maxReintentos, evento.eventoId(), evento.citaId(), e.getMessage());
            channel.basicNack(tag, false, false);

        } catch (ErrorPermanente e) {
            log.warn("[{}] error permanente eventoId={} cita={}: {} -> NACK sin reintentar, va a la DLQ",
                    cola, evento.eventoId(), evento.citaId(), e.getMessage());
            channel.basicNack(tag, false, false);

        } catch (ProcesamientoInterrumpido e) {
            // El microservicio se está apagando: el mensaje no tiene la culpa, vuelve a la cola
            log.warn("[{}] procesamiento interrumpido eventoId={} -> NACK reencolando",
                    cola, evento.eventoId());
            channel.basicNack(tag, false, true);

        } catch (RuntimeException e) {
            // Error no clasificado (normalmente un bug): reintentar no lo arregla, se trata como permanente
            log.error("[{}] error inesperado eventoId={} cita={}: {} -> NACK sin reencolar, va a la DLQ",
                    cola, evento.eventoId(), evento.citaId(), e.toString(), e);
            channel.basicNack(tag, false, false);
        }
    }

    /** Un evento sin estos datos no se puede procesar nunca: es un error permanente. */
    private void validar(CitaEvento evento) {
        if (evento.eventoId() == null || evento.citaId() == null || evento.tipo() == null) {
            throw new ErrorPermanente("datos invalidos: faltan eventoId, citaId o tipo");
        }
    }

    /**
     * Primer intento + hasta maxReintentos reintentos SOLO ante ErrorTransitorio.
     * Esperas: intervaloInicial, x multiplicador, ... (1 s, 2 s, 4 s con la configuración actual).
     * Se reintenta aquí, en el mismo consumidor: el mensaje sigue sin ACK y, con prefetch 1,
     * nadie más lo toma mientras tanto.
     */
    private void ejecutarConReintentos(String cola, CitaEvento evento, String simularFallo,
            Consumer<CitaEvento> accion) {
        long espera = intervaloInicialMs;
        for (int reintento = 0;; reintento++) {
            try {
                simulador.aplicar(simularFallo);
                accion.accept(evento);
                if (reintento > 0) {
                    log.info("[{}] recuperado en el reintento {} eventoId={}", cola, reintento, evento.eventoId());
                }
                return;
            } catch (ErrorTransitorio e) {
                if (reintento >= maxReintentos) {
                    throw e;
                }
                log.warn("[{}] error transitorio eventoId={} (intento {} de {}): {} -> reintento {}/{} en {} ms",
                        cola, evento.eventoId(), reintento + 1, maxReintentos + 1, e.getMessage(),
                        reintento + 1, maxReintentos, espera);
                dormir(espera);
                espera = Math.round(espera * multiplicador);
            }
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcesamientoInterrumpido();
        }
    }

    /** El hilo se interrumpió durante la espera de un reintento (apagado del microservicio). */
    private static final class ProcesamientoInterrumpido extends RuntimeException {
    }
}
