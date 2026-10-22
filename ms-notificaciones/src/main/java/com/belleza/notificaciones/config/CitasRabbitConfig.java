package com.belleza.notificaciones.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de los eventos de citas.
 *
 * <pre>
 * salon.citas.eventos (TOPIC) ── "cita.*" ────────► salon.notificaciones   (guía 2.2.1, pub/sub)
 *                             └─ "cita.aprobada" ─► salon.comprobantes
 * salon.citas.directo (DIRECT) ─ "cita.creada" ───► salon.aprobaciones     (guía 2.1.3)
 * </pre>
 *
 * Exchanges, colas y mensajes son durables/persistentes: sobreviven a un reinicio de RabbitMQ.
 */
@Configuration
public class CitasRabbitConfig {

    /** Nombre del container factory que usan los consumidores de citas. */
    public static final String FACTORY_CITAS = "citasListenerFactory";

    // ───────── Exchanges (misma declaración que ms-reservas: durable, sin auto-delete) ─────────

    @Bean
    public DirectExchange citasExchange(@Value("${salon.mensajeria.exchange.citas}") String nombre) {
        return new DirectExchange(nombre, true, false);
    }

    @Bean
    public TopicExchange eventosExchange(@Value("${salon.mensajeria.exchange.eventos}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    // ───────── Colas durables ─────────

    @Bean
    public Queue notificacionesQueue(@Value("${salon.mensajeria.colas.notificaciones}") String nombre) {
        return new Queue(nombre, true);
    }

    @Bean
    public Queue aprobacionesQueue(@Value("${salon.mensajeria.colas.aprobaciones}") String nombre) {
        return new Queue(nombre, true);
    }

    @Bean
    public Queue comprobantesQueue(@Value("${salon.mensajeria.colas.comprobantes}") String nombre) {
        return new Queue(nombre, true);
    }

    // ───────── Bindings ─────────

    @Bean
    public Declarables bindingsCitas(DirectExchange citasExchange,
            TopicExchange eventosExchange,
            Queue notificacionesQueue,
            Queue aprobacionesQueue,
            Queue comprobantesQueue,
            @Value("${salon.mensajeria.patrones.todas-las-citas}") String todasLasCitas,
            @Value("${salon.mensajeria.routing.creada}") String creada,
            @Value("${salon.mensajeria.routing.aprobada}") String aprobada) {
        return new Declarables(
                // La clienta se entera de todo lo que pasa con su cita
                BindingBuilder.bind(notificacionesQueue).to(eventosExchange).with(todasLasCitas),
                // Solo las aprobadas generan comprobante
                BindingBuilder.bind(comprobantesQueue).to(eventosExchange).with(aprobada),
                // A la aprobadora solo le interesan las citas nuevas
                BindingBuilder.bind(aprobacionesQueue).to(citasExchange).with(creada));
    }

    // ───────── Consumo ─────────

    /**
     * Factory de los consumidores de citas:
     *  - JSON → CitaEvento.
     *  - ACK MANUAL: el consumidor confirma (basicAck) recién cuando terminó; si se cae antes,
     *    RabbitMQ reentrega el mensaje (at-least-once).
     *  - prefetch 1: no recibe el siguiente mensaje hasta confirmar el actual.
     * No es el factory por defecto, así el ejemplo de saludos sigue con texto plano y ACK automático.
     */
    @Bean(FACTORY_CITAS)
    public SimpleRabbitListenerContainerFactory citasListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new JacksonJsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        return factory;
    }
}
