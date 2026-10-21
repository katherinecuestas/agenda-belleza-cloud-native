package com.belleza.notificaciones.saludo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SaludoProductor {

    private static final Logger log = LoggerFactory.getLogger(SaludoProductor.class);

    private final RabbitTemplate rabbitTemplate;
    private final String nombreCola;

    public SaludoProductor(RabbitTemplate rabbitTemplate,
            @Value("${salon.mensajeria.colas.saludos}") String nombreCola) {
        this.rabbitTemplate = rabbitTemplate;
        this.nombreCola = nombreCola;
    }

    /** Exchange por defecto (""): la routing key es el nombre de la cola. */
    public void enviar(String mensaje) {
        rabbitTemplate.convertAndSend(nombreCola, mensaje);
        log.info("[→] Enviado: '{}' a la cola {}", mensaje, nombreCola);
    }
}
