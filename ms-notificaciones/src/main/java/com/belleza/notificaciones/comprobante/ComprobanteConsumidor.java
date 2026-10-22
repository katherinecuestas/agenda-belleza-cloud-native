package com.belleza.notificaciones.comprobante;

import com.belleza.notificaciones.config.CitasRabbitConfig;
import com.belleza.notificaciones.evento.Ascii;
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

/** Genera el comprobante de una cita aprobada (TOPIC, solo cita.aprobada). */
@Component
public class ComprobanteConsumidor {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteConsumidor.class);

    private final ConsumoConAck consumo;
    private final String cola;

    public ComprobanteConsumidor(ConsumoConAck consumo,
            @Value("${salon.mensajeria.colas.comprobantes}") String cola) {
        this.consumo = consumo;
        this.cola = cola;
    }

    @RabbitListener(queues = "${salon.mensajeria.colas.comprobantes}",
            containerFactory = CitasRabbitConfig.FACTORY_CITAS)
    public void recibir(CitaEvento evento, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        consumo.procesar(cola, evento, channel, tag, e ->
                log.info("[COMPROBANTE] generado para cita {} ({}, {})",
                        e.citaId(), Ascii.de(e.servicio()), e.inicio()));
    }
}
