package com.belleza.reservas.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
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
 *
 * Destinos:
 *  - TOPIC salon.citas.eventos: todos los eventos (pub/sub, guía 2.2.1).
 *  - DIRECT salon.citas.directo: solo cita.creada, para la aprobadora (guía 2.1.3).
 */
@Component
public class CitaEventoPublicador {

    private static final Logger log = LoggerFactory.getLogger(CitaEventoPublicador.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeDirecto;
    private final String exchangeEventos;
    private final Map<TipoEventoCita, String> routingKeys = new EnumMap<>(TipoEventoCita.class);

    public CitaEventoPublicador(RabbitTemplate rabbitTemplate,
            @Value("${salon.mensajeria.exchange.citas}") String exchangeDirecto,
            @Value("${salon.mensajeria.exchange.eventos}") String exchangeEventos,
            @Value("${salon.mensajeria.routing.creada}") String creada,
            @Value("${salon.mensajeria.routing.aprobada}") String aprobada,
            @Value("${salon.mensajeria.routing.rechazada}") String rechazada,
            @Value("${salon.mensajeria.routing.cancelada}") String cancelada) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeDirecto = exchangeDirecto;
        this.exchangeEventos = exchangeEventos;
        routingKeys.put(TipoEventoCita.CREADA, creada);
        routingKeys.put(TipoEventoCita.APROBADA, aprobada);
        routingKeys.put(TipoEventoCita.RECHAZADA, rechazada);
        routingKeys.put(TipoEventoCita.CANCELADA, cancelada);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(CitaEvento evento) {
        String routingKey = routingKeys.get(evento.tipo());
        publicarEn(exchangeEventos, routingKey, evento);
        if (evento.tipo() == TipoEventoCita.CREADA) {
            publicarEn(exchangeDirecto, routingKey, evento);
        }
    }

    /** Cada destino por separado: si uno falla, se intenta igual el otro. */
    private void publicarEn(String exchange, String routingKey, CitaEvento evento) {
        String eventoId = evento.eventoId().toString();
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, evento,
                    mensaje -> {
                        // Durabilidad: el mensaje se escribe a disco (además, exchange y cola son durables)
                        mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        // El consumidor usa el messageId/eventoId para descartar duplicados
                        mensaje.getMessageProperties().setMessageId(eventoId);
                        return mensaje;
                    },
                    new CorrelacionEvento(eventoId, exchange, routingKey));
            log.info("Evento publicado: {} cita={} exchange={} routingKey={} eventoId={}",
                    evento.tipo(), evento.citaId(), exchange, routingKey, eventoId);
        } catch (RuntimeException e) {
            // La cita ya está guardada: un fallo de mensajería (AmqpException u otro) no debe
            // llegar al controller, o la API respondería 500 por una operación que sí se hizo
            log.error("No se pudo publicar el evento {} eventoId={} citaId={} exchange={}: {}",
                    evento.tipo(), eventoId, evento.citaId(), exchange, e.getMessage());
        }
    }
}
