package com.belleza.notificaciones.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
public class RabbitConfig {

    /**
     * Define la cola "salon.saludos"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     *
     * quorum: replicada en los 3 nodos del cluster
     */
    @Bean
    public Queue saludos(@Value("${salon.mensajeria.colas.saludos}") String nombre) {
        return QueueBuilder.durable(nombre).quorum().build();
    }
}
