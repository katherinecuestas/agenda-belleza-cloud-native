package com.belleza.notificaciones.saludo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Productor (Sender) - Envía mensajes a la cola 'salon.saludos'
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Service
public class SaludoProductor {

    private static final Logger log = LoggerFactory.getLogger(SaludoProductor.class);

    /**
     * RabbitTemplate es la clase principal de Spring AMQP
     * para enviar mensajes.
     *
     * Proporciona métodos simplificados para:
     * - convertAndSend(): convierte objeto a JSON y envía
     * - convertSendAndReceive(): espera respuesta
     */
    private final RabbitTemplate rabbitTemplate;
    private final String nombreCola;

    /**
     * Spring inyecta automáticamente la instancia de RabbitTemplate
     */
    public SaludoProductor(RabbitTemplate rabbitTemplate,
            @Value("${salon.mensajeria.colas.saludos}") String nombreCola) {
        this.rabbitTemplate = rabbitTemplate;
        this.nombreCola = nombreCola;
    }

    /**
     * Método para enviar un mensaje simple
     *
     * Parámetros:
     * - exchange: "" (vacío) significa usar el exchange por defecto
     * - routingKey: "salon.saludos" (nombre de la cola destino)
     * - message: el contenido del mensaje
     */
    public void enviar(String mensaje) {
        // Enviar el mensaje a la cola
        // exchange="" usa el exchange por defecto (DIRECT)
        // routingKey="salon.saludos" especifica la cola destino
        rabbitTemplate.convertAndSend(nombreCola, mensaje);
        log.info("[→] Enviado: '{}' a la cola {}", mensaje, nombreCola);
    }
}
