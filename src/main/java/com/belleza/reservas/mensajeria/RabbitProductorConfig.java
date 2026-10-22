package com.belleza.reservas.mensajeria;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lado productor: solo declara el exchange. Las colas y sus bindings son
 * decisión de cada consumidor (ms-notificaciones).
 */
@Configuration
public class RabbitProductorConfig {

    /** Durable y sin auto-delete: debe coincidir con la declaración del consumidor. */
    @Bean
    public DirectExchange citasExchange(@Value("${salon.mensajeria.exchange.citas}") String nombre) {
        return new DirectExchange(nombre, true, false);
    }

    /** Los eventos viajan como JSON (Jackson 3). Spring Boot lo conecta al RabbitTemplate. */
    @Bean
    public MessageConverter mensajeriaJsonConverter() {
        return new JacksonJsonMessageConverter();
    }
}
