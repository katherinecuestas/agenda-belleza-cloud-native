package com.belleza.notificaciones.saludo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor (Receiver) - Recibe mensajes de la cola 'salon.saludos'
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Component
public class SaludoConsumidor {

    private static final Logger log = LoggerFactory.getLogger(SaludoConsumidor.class);

    /**
     * Este método se ejecuta automáticamente cada vez que
     * llega un mensaje a la cola 'salon.saludos'
     *
     * La anotación @RabbitListener:
     * - Registra este método como listener de mensajes
     * - queues="salon.saludos" especifica la cola a escuchar
     * - Spring AMQP maneja automáticamente:
     *   - Conexión a RabbitMQ
     *   - Deserialización del mensaje
     *   - Confirmación (ack) del mensaje
     *   - Manejo de excepciones
     *
     * String mensaje: Spring AMQP deserializa automáticamente el mensaje
     */
    @RabbitListener(queues = "${salon.mensajeria.colas.saludos}")
    public void recibir(String mensaje) {
        log.info("[✓] Recibido: '{}'", mensaje);
    }
}
