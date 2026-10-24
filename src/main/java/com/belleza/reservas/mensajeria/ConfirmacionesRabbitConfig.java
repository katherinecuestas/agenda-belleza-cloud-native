package com.belleza.reservas.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ
 *
 * Publisher confirms: el broker avisa si recibió cada mensaje enviado
 *
 * Basada en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Configuration
public class ConfirmacionesRabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacionesRabbitConfig.class);

    /**
     * Agrega al RabbitTemplate los avisos del broker:
     * - ConfirmCallback: el broker aceptó (ack) o rechazó (nack) el mensaje
     * - ReturnsCallback: el mensaje no llegó a ninguna cola
     */
    @Bean
    public RabbitTemplateCustomizer confirmacionesCustomizer() {
        return template -> {
            template.setConfirmCallback(ConfirmacionesRabbitConfig::alConfirmar);
            template.setReturnsCallback(ConfirmacionesRabbitConfig::alDevolver);
        };
    }

    /** Registra en el log si el broker aceptó o rechazó el mensaje */
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

    /** Registra en el log un mensaje que no llegó a ninguna cola */
    private static void alDevolver(ReturnedMessage devuelto) {
        log.warn("Mensaje sin cola de destino routingKey={} exchange={} eventoId={}: {}",
                devuelto.getRoutingKey(), devuelto.getExchange(),
                devuelto.getMessage().getMessageProperties().getMessageId(), devuelto.getReplyText());
    }
}
