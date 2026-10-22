package com.belleza.rabbitadmin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Carga el contexto completo SIN RabbitMQ ni Cognito: la conexión de Spring AMQP se abre
 * recién en la primera operación, y el JwtDecoder (SupplierJwtDecoder) contacta a Cognito
 * recién con el primer token. Así el test corre en CI.
 */
@SpringBootTest
class RabbitAdminApplicationTests {

	@Test
	void contextLoads() {
	}

}
