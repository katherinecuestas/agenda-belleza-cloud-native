package com.belleza.rabbitadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Aplicación principal de RabbitMQ con Spring Boot
 *
 * API para administrar RabbitMQ: crear y borrar colas, exchanges y bindings
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RabbitAdminApplication {

	public static void main(String[] args) {
		// Levanta la aplicación Spring Boot
		SpringApplication.run(RabbitAdminApplication.class, args);
	}

}
