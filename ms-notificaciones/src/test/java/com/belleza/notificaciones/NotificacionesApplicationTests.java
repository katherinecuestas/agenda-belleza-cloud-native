package com.belleza.notificaciones;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Carga el contexto completo SIN RabbitMQ: los listeners no arrancan y la
 * conexión de Spring AMQP es perezosa (se abre recién al enviar o consumir).
 * Así el test pasa en CI, donde no hay broker.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class NotificacionesApplicationTests {

	@Test
	void contextLoads() {
	}

}
