package com.belleza.notificaciones.aprobacion;

import com.belleza.notificaciones.config.CitasRabbitConfig;
import com.belleza.notificaciones.error.SimuladorFallos;
import com.belleza.notificaciones.evento.CitaEvento;
import com.belleza.notificaciones.evento.ConsumoConAck;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor (Receiver) - Recibe mensajes de la cola 'salon.aprobaciones'
 *
 * Avisa a la aprobadora que hay una cita nueva por revisar (solo cita.creada)
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Component
public class AvisoAprobadoraConsumidor {

    private static final Logger log = LoggerFactory.getLogger(AvisoAprobadoraConsumidor.class);

    private final ConsumoConAck consumo;
    private final String cola;

    public AvisoAprobadoraConsumidor(ConsumoConAck consumo,
            @Value("${salon.mensajeria.colas.aprobaciones}") String cola) {
        this.consumo = consumo;
        this.cola = cola;
    }

    /**
     * Este método se ejecuta automáticamente cada vez que
     * llega un mensaje a la cola 'salon.aprobaciones'
     *
     * La anotación @RabbitListener:
     * - Registra este método como listener de mensajes
     * - queues="salon.aprobaciones" especifica la cola a escuchar
     *
     * Parámetros:
     * - evento: Spring AMQP deserializa automáticamente el mensaje
     * - channel: Acceso al canal AMQP para confirmación manual (manual acks)
     * - tag: identifica el mensaje en basicAck() / basicNack()
     * - simularFallo: header opcional para la demo de errores
     */
    @RabbitListener(queues = "${salon.mensajeria.colas.aprobaciones}",
            containerFactory = CitasRabbitConfig.FACTORY_CITAS)
    public void recibir(CitaEvento evento, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag,
            @Header(name = SimuladorFallos.HEADER, required = false) String simularFallo) throws IOException {
        consumo.procesar(cola, evento, simularFallo, channel, tag, e ->
                log.info("[AVISO APROBADORA] nueva cita {} esperando revision (eventoId={})",
                        e.citaId(), e.eventoId()));
    }
}
