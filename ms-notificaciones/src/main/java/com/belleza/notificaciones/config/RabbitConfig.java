package com.belleza.notificaciones.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara las colas del microservicio. RabbitAdmin las crea en el broker al
 * abrir la primera conexión, si todavía no existen.
 */
@Configuration
public class RabbitConfig {

    /** Cola simple de la guía 2.1.2: se publica en ella por el exchange por defecto. */
    @Bean
    public Queue saludos(@Value("${salon.mensajeria.colas.saludos}") String nombre) {
        // durable = true: la cola sobrevive a un reinicio de RabbitMQ
        return new Queue(nombre, true);
    }
}
