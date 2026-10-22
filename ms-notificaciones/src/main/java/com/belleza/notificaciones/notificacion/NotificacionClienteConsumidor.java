package com.belleza.notificaciones.notificacion;

import com.belleza.notificaciones.config.CitasRabbitConfig;
import com.belleza.notificaciones.evento.CitaEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Avisa a la clienta de cada cambio en su cita (recibe las 4 routing keys). */
@Component
public class NotificacionClienteConsumidor {

    private static final Logger log = LoggerFactory.getLogger(NotificacionClienteConsumidor.class);

    @RabbitListener(queues = "${salon.mensajeria.colas.notificaciones}",
            containerFactory = CitasRabbitConfig.FACTORY_CITAS)
    public void recibir(CitaEvento evento) {
        // Solo ASCII en el log: la consola de Windows no muestra bien tildes ni simbolos
        log.info("[NOTIFICACION CLIENTA] cita {} {} (eventoId={})",
                evento.citaId(), evento.tipo(), evento.eventoId());
    }
}
