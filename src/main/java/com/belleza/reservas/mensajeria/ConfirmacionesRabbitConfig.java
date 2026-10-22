package com.belleza.reservas.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publisher confirms y returns (guía 2.2.1). Es la mitad broker → productor:
 *  - ConfirmCallback: el broker aceptó (ack) o no (nack) el mensaje.
 *  - ReturnsCallback: el broker lo aceptó, pero ninguna cola coincidía con la routing key.
 * Requiere publisher-confirm-type=correlated, publisher-returns=true y template.mandatory=true.
 */
@Configuration
public class ConfirmacionesRabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacionesRabbitConfig.class);

    @Bean
    public RabbitTemplateCustomizer confirmacionesCustomizer() {
        return template -> {
            template.setConfirmCallback(ConfirmacionesRabbitConfig::alConfirmar);
            template.setReturnsCallback(ConfirmacionesRabbitConfig::alDevolver);
        };
    }

    private static void alConfirmar(CorrelationData correlacion, boolean ack, String causa) {
        String eventoId = correlacion != null ? correlacion.getId() : "desconocido";
        String destino = correlacion instanceof CorrelacionEvento c
                ? c.getExchange() + " / " + c.getRoutingKey()
                : "?";
        if (ack) {
            log.info("Confirmado por el broker eventoId={} ({})", eventoId, destino);
        } else {
            log.warn("El broker RECHAZO (nack) el evento eventoId={} ({}): {}", eventoId, destino, causa);
        }
    }

    private static void alDevolver(ReturnedMessage devuelto) {
        log.warn("Mensaje sin cola de destino routingKey={} exchange={} eventoId={}: {}",
                devuelto.getRoutingKey(), devuelto.getExchange(),
                devuelto.getMessage().getMessageProperties().getMessageId(), devuelto.getReplyText());
    }
}
