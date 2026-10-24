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
 * Confirmación manual (manual acks) que comparten los consumidores de citas
 *
 * - Éxito: basicAck()
 * - Error transitorio: reintenta con backoff (1 s, 2 s, 4 s); si se agotan, basicNack() a la DLQ
 * - Error permanente: basicNack() a la DLQ, sin reintentar
 * - Mensaje repetido (mismo eventoId): basicAck() sin procesarlo de nuevo
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
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

    /**
     * Procesa un mensaje y lo confirma o lo rechaza
     *
     * Parámetros:
     * - cola: nombre de la cola que recibió el mensaje
     * - evento: el mensaje ya deserializado
     * - simularFallo: header opcional para la demo de errores
     * - channel: Acceso al canal AMQP para confirmación manual (manual acks)
     * - tag: identifica el mensaje en basicAck() / basicNack()
     * - accion: Aquí va tu lógica de procesamiento del mensaje
     */
    public void procesar(String cola, CitaEvento evento, String simularFallo, Channel channel, long tag,
            Consumer<CitaEvento> accion) throws IOException {
        // Idempotencia: si este eventoId ya se procesó en esta cola, no se procesa de nuevo
        if (evento.eventoId() != null && registro.yaProcesado(cola, evento.eventoId())) {
            log.info("[{}] duplicado ignorado eventoId={} cita={}", cola, evento.eventoId(), evento.citaId());
            // Confirmación manual del mensaje (si usas manual acks)
            // basicAck(): Confirma que el mensaje fue procesado exitosamente
            channel.basicAck(tag, false);
            return;
        }

        try {
            validar(evento);
            // Si lanzas una excepción, Spring AMQP reintentará el mensaje
            // según la configuración de retry
            // (aquí el retry lo hace ejecutarConReintentos, con salon.mensajeria.reintentos)
            ejecutarConReintentos(cola, evento, simularFallo, accion);
            registro.marcarProcesado(cola, evento.eventoId());
            // Confirmación manual del mensaje (si usas manual acks)
            // basicAck(): Confirma que el mensaje fue procesado exitosamente
            channel.basicAck(tag, false);

        } catch (ErrorTransitorio e) {
            log.warn("[{}] agotados {} reintentos eventoId={} cita={}: {} -> NACK sin reencolar, va a la DLQ",
                    cola, maxReintentos, evento.eventoId(), evento.citaId(), e.getMessage());
            // basicNack(): Rechaza el mensaje
            // requeue=false: no vuelve a la cola, va a la DLQ
            channel.basicNack(tag, false, false);

        } catch (ErrorPermanente e) {
            log.warn("[{}] error permanente eventoId={} cita={}: {} -> NACK sin reintentar, va a la DLQ",
                    cola, evento.eventoId(), evento.citaId(), e.getMessage());
            // basicNack(): Rechaza el mensaje
            // requeue=false: no vuelve a la cola, va a la DLQ
            channel.basicNack(tag, false, false);

        } catch (ProcesamientoInterrumpido e) {
            log.warn("[{}] procesamiento interrumpido eventoId={} -> NACK reencolando",
                    cola, evento.eventoId());
            // Rechazar el mensaje y requearlo (si usas manual acks)
            // basicNack(): Rechaza el mensaje y lo devuelve a la cola
            channel.basicNack(tag, false, true);

        } catch (RuntimeException e) {
            log.error("[{}] error inesperado eventoId={} cita={}: {} -> NACK sin reencolar, va a la DLQ",
                    cola, evento.eventoId(), evento.citaId(), e.toString(), e);
            // basicNack(): Rechaza el mensaje
            // requeue=false: no vuelve a la cola, va a la DLQ
            channel.basicNack(tag, false, false);
        }
    }

    /** Un mensaje sin eventoId, citaId o tipo es un error permanente */
    private void validar(CitaEvento evento) {
        if (evento.eventoId() == null || evento.citaId() == null || evento.tipo() == null) {
            throw new ErrorPermanente("datos invalidos: faltan eventoId, citaId o tipo");
        }
    }

    /**
     * Reintentos con backoff, solo ante ErrorTransitorio
     *
     * - intervalo-inicial-ms: 1000  # primera espera
     * - multiplicador: 2.0          # cada espera es el doble de la anterior
     * - max-reintentos: 3           # esperas de 1 s, 2 s y 4 s
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

    /** Espera entre reintentos */
    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcesamientoInterrumpido();
        }
    }

    /** El microservicio se está apagando durante la espera de un reintento */
    private static final class ProcesamientoInterrumpido extends RuntimeException {
    }
}
