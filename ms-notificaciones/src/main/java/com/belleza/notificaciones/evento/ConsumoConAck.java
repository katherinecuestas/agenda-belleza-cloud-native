package com.belleza.notificaciones.evento;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Lo que comparten todos los consumidores de citas con ACK manual (guía 2.2.1):
 *  1. eventoId ya procesado en esta cola → "duplicado ignorado" y ACK (no se repite el efecto).
 *  2. procesar → marcar como procesado → basicAck.
 *  3. excepción → log.error y basicNack sin reencolar (la DLQ llega en la guía 2.2.2).
 */
@Component
public class ConsumoConAck {

    private static final Logger log = LoggerFactory.getLogger(ConsumoConAck.class);

    private final RegistroEventosProcesados registro;

    public ConsumoConAck(RegistroEventosProcesados registro) {
        this.registro = registro;
    }

    public void procesar(String cola, CitaEvento evento, Channel channel, long tag,
            Consumer<CitaEvento> accion) throws IOException {
        try {
            if (evento.eventoId() != null && registro.yaProcesado(cola, evento.eventoId())) {
                log.info("[{}] duplicado ignorado eventoId={} cita={}", cola, evento.eventoId(), evento.citaId());
                channel.basicAck(tag, false);
                return;
            }

            accion.accept(evento);

            if (evento.eventoId() != null) {
                registro.marcarProcesado(cola, evento.eventoId());
            }
            // multiple=false: confirma solo ESTE mensaje
            channel.basicAck(tag, false);
        } catch (RuntimeException e) {
            log.error("[{}] fallo al procesar eventoId={} cita={}: {}",
                    cola, evento.eventoId(), evento.citaId(), e.getMessage(), e);
            // requeue=false: no vuelve a la cola (evita un bucle infinito con un mensaje que siempre falla)
            channel.basicNack(tag, false, false);
        }
    }
}
