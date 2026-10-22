package com.belleza.notificaciones.saludo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SaludoConsumidor {

    private static final Logger log = LoggerFactory.getLogger(SaludoConsumidor.class);

    @RabbitListener(queues = "${salon.mensajeria.colas.saludos}")
    public void recibir(String mensaje) {
        log.info("[✓] Recibido: '{}'", mensaje);
    }
}
