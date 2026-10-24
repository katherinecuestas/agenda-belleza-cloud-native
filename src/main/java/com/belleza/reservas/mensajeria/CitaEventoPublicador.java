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
 * Productor (Sender) - Envía mensajes a los exchanges
 * 'salon.citas.eventos' (todos los eventos) y 'salon.citas.directo' (solo cita.creada)
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Component
public class CitaEventoPublicador {

    private static final Logger log = LoggerFactory.getLogger(CitaEventoPublicador.class);

    /**
     * RabbitTemplate es la clase principal de Spring AMQP
     * para enviar mensajes.
     *
     * Proporciona métodos simplificados para:
     * - convertAndSend(): convierte objeto a JSON y envía
     * - convertSendAndReceive(): espera respuesta
     */
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeDirecto;
    private final String exchangeEventos;
    private final Map<TipoEventoCita, String> routingKeys = new EnumMap<>(TipoEventoCita.class);

    /**
     * Spring inyecta automáticamente la instancia de RabbitTemplate
     * (y los nombres de exchanges y routing keys de application.properties)
     */
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

    /**
     * Método para enviar un evento de cita
     *
     * AFTER_COMMIT: se ejecuta solo cuando la cita ya quedó guardada
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(CitaEvento evento) {
        String routingKey = routingKeys.get(evento.tipo());
        publicarEn(exchangeEventos, routingKey, evento);
        if (evento.tipo() == TipoEventoCita.CREADA) {
            publicarEn(exchangeDirecto, routingKey, evento);
        }
    }

    /**
     * Versión con exchange explícito
     * (para casos más avanzados)
     *
     * Parámetros:
     * - exchange: "salon.citas.eventos" o "salon.citas.directo"
     * - routingKey: cita.creada, cita.aprobada, cita.rechazada o cita.cancelada
     * - evento: el contenido del mensaje
     */
    private void publicarEn(String exchange, String routingKey, CitaEvento evento) {
        String eventoId = evento.eventoId().toString();
        try {
            // Enviar el mensaje al exchange
            // convertAndSend(): Convierte el objeto Java a JSON y lo envía a RabbitMQ
            rabbitTemplate.convertAndSend(exchange, routingKey, evento,
                    mensaje -> {
                        // Mensaje persistente: sobrevive si RabbitMQ se reinicia
                        mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        // messageId = eventoId: el consumidor lo usa para ignorar repetidos
                        mensaje.getMessageProperties().setMessageId(eventoId);
                        return mensaje;
                    },
                    new CorrelacionEvento(eventoId, exchange, routingKey));
            log.info("Evento publicado: {} cita={} exchange={} routingKey={} eventoId={}",
                    evento.tipo(), evento.citaId(), exchange, routingKey, eventoId);
        } catch (RuntimeException e) {
            // Si RabbitMQ falla, solo se registra: la cita ya quedó guardada
            log.error("No se pudo publicar el evento {} eventoId={} citaId={} exchange={}: {}",
                    evento.tipo(), eventoId, evento.citaId(), exchange, e.getMessage());
        }
    }
}
