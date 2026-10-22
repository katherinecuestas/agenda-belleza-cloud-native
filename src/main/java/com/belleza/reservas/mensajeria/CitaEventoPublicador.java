package com.belleza.reservas.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumMap;
import java.util.Map;

/**
 * Puente entre los eventos de Spring y RabbitMQ. Corre DESPUÉS del commit:
 * solo se publica lo que de verdad quedó guardado.
 */
@Component
public class CitaEventoPublicador {

    private static final Logger log = LoggerFactory.getLogger(CitaEventoPublicador.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final Map<TipoEventoCita, String> routingKeys = new EnumMap<>(TipoEventoCita.class);

    public CitaEventoPublicador(RabbitTemplate rabbitTemplate,
            @Value("${salon.mensajeria.exchange.citas}") String exchange,
            @Value("${salon.mensajeria.routing.creada}") String creada,
            @Value("${salon.mensajeria.routing.aprobada}") String aprobada,
            @Value("${salon.mensajeria.routing.rechazada}") String rechazada,
            @Value("${salon.mensajeria.routing.cancelada}") String cancelada) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        routingKeys.put(TipoEventoCita.CREADA, creada);
        routingKeys.put(TipoEventoCita.APROBADA, aprobada);
        routingKeys.put(TipoEventoCita.RECHAZADA, rechazada);
        routingKeys.put(TipoEventoCita.CANCELADA, cancelada);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(CitaEvento evento) {
        String routingKey = routingKeys.get(evento.tipo());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, evento);
            log.info("Evento publicado: {} cita={} routingKey={} eventoId={}",
                    evento.tipo(), evento.citaId(), routingKey, evento.eventoId());
        } catch (RuntimeException e) {
            // La cita ya está guardada: un fallo de mensajería (AmqpException u otro) no debe
            // llegar al controller, o la API respondería 500 por una operación que sí se hizo
            log.error("No se pudo publicar el evento {} eventoId={} citaId={}: {}",
                    evento.tipo(), evento.eventoId(), evento.citaId(), e.getMessage());
        }
    }
}
