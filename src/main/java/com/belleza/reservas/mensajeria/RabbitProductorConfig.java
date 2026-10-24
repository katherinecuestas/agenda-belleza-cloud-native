package com.belleza.reservas.mensajeria;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ
 *
 * Basada en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Configuration
public class RabbitProductorConfig {

    /**
     * Define el exchange "salon.citas.directo" (DIRECT)
     *
     * Exchange explícito (para casos más avanzados):
     * la routing key debe coincidir exacta con la del binding
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public DirectExchange citasExchange(@Value("${salon.mensajeria.exchange.citas}") String nombre) {
        return new DirectExchange(nombre, true, false);
    }

    /**
     * Define el exchange "salon.citas.eventos" (TOPIC)
     *
     * Exchange explícito (para casos más avanzados):
     * la routing key se compara con patrones, por ejemplo "cita.*"
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public TopicExchange eventosExchange(@Value("${salon.mensajeria.exchange.eventos}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    /**
     * Conversor JSON del RabbitTemplate
     *
     * convertAndSend(): Convierte el objeto Java a JSON y lo envía a RabbitMQ
     */
    @Bean
    public MessageConverter mensajeriaJsonConverter() {
        return new JacksonJsonMessageConverter();
    }
}
