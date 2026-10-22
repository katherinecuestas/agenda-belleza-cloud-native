package com.belleza.notificaciones.notificacion;

import com.belleza.notificaciones.config.CitasRabbitConfig;
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

/** Avisa a la clienta de cada cambio en su cita (TOPIC, patrón "cita.*"). */
@Component
public class NotificacionClienteConsumidor {

    private static final Logger log = LoggerFactory.getLogger(NotificacionClienteConsumidor.class);

    private final ConsumoConAck consumo;
    private final String cola;

    public NotificacionClienteConsumidor(ConsumoConAck consumo,
            @Value("${salon.mensajeria.colas.notificaciones}") String cola) {
        this.consumo = consumo;
        this.cola = cola;
    }

    @RabbitListener(queues = "${salon.mensajeria.colas.notificaciones}",
            containerFactory = CitasRabbitConfig.FACTORY_CITAS)
    public void recibir(CitaEvento evento, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        consumo.procesar(cola, evento, channel, tag, e ->
                log.info("[NOTIFICACION CLIENTA] cita {} {} (eventoId={})",
                        e.citaId(), e.tipo(), e.eventoId()));
    }
}
