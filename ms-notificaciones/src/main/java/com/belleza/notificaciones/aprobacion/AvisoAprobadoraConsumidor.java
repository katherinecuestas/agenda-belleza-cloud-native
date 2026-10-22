package com.belleza.notificaciones.aprobacion;

import com.belleza.notificaciones.config.CitasRabbitConfig;
import com.belleza.notificaciones.evento.CitaEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Avisa a la aprobadora que hay una cita nueva por revisar (solo cita.creada). */
@Component
public class AvisoAprobadoraConsumidor {

    private static final Logger log = LoggerFactory.getLogger(AvisoAprobadoraConsumidor.class);

    @RabbitListener(queues = "${salon.mensajeria.colas.aprobaciones}",
            containerFactory = CitasRabbitConfig.FACTORY_CITAS)
    public void recibir(CitaEvento evento) {
        // Solo ASCII en el log: la consola de Windows no muestra bien tildes ni simbolos
        log.info("[AVISO APROBADORA] nueva cita {} esperando revision (eventoId={})",
                evento.citaId(), evento.eventoId());
    }
}
