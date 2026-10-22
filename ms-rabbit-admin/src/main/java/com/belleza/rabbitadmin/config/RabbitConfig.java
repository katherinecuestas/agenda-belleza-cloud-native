package com.belleza.rabbitadmin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitAdmin explícito: Spring Boot 4 lo registra solo como AmqpAdmin (la interfaz), y el
 * servicio necesita el tipo concreto (getQueueInfo, purgeQueue, getRabbitTemplate...).
 * Como también es un AmqpAdmin, el que trae Spring Boot no se crea: queda uno solo.
 *
 * La conexión sigue siendo perezosa: se abre en la primera operación, no al arrancar.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
