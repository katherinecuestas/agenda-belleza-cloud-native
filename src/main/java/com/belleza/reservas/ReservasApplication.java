package com.belleza.reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación principal de RabbitMQ con Spring Boot
 *
 * Basada en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-boot/reference/
 */
@SpringBootApplication
public class ReservasApplication {

	public static void main(String[] args) {
		// Levanta la aplicación Spring Boot
		SpringApplication.run(ReservasApplication.class, args);
	}

}
