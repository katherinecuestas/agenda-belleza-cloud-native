package com.belleza.notificaciones.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de los eventos de citas.
 *
 * <pre>
 * salon.citas.eventos (TOPIC) ── "cita.*" ────────► salon.notificaciones ─┐
 *                             └─ "cita.aprobada" ─► salon.comprobantes ───┤ rechazado / vencido /
 * salon.citas.directo (DIRECT) ─ "cita.creada" ───► salon.aprobaciones ───┤ desbordado
 *                                                                         ▼
 *            salon.citas.dlx (DIRECT) ── "notificaciones" ─► salon.notificaciones.dlq
 *                                     ├─ "comprobantes" ───► salon.comprobantes.dlq
 *                                     └─ "aprobaciones" ───► salon.aprobaciones.dlq
 * </pre>
 *
 * Todo es durable (exchanges, colas) y los mensajes llegan persistentes: sobrevive a un reinicio.
 *
 * OJO: los argumentos x-* de una cola no se pueden cambiar en caliente. Si una cola ya existe con
 * otros argumentos, RabbitMQ responde PRECONDITION_FAILED (406): hay que borrarla y dejar que se
 * vuelva a declarar.
 */
@Configuration
public class CitasRabbitConfig {

    /** Nombre del container factory que usan los consumidores de citas. */
    public static final String FACTORY_CITAS = "citasListenerFactory";
    /** Nombre del container factory del consumidor de las DLQ (sin conversión JSON). */
    public static final String FACTORY_DLQ = "dlqListenerFactory";

    @Value("${salon.mensajeria.dlx.exchange}")
    private String dlx;
    @Value("${salon.mensajeria.limites.ttl-ms}")
    private int ttlColaMs;
    @Value("${salon.mensajeria.limites.max-length}")
    private int maxLength;
    @Value("${salon.mensajeria.dlq.ttl-ms}")
    private int ttlDlqMs;

    // ───────── Exchanges (misma declaración que ms-reservas: durable, sin auto-delete) ─────────

    @Bean
    public DirectExchange citasExchange(@Value("${salon.mensajeria.exchange.citas}") String nombre) {
        return new DirectExchange(nombre, true, false);
    }

    @Bean
    public TopicExchange eventosExchange(@Value("${salon.mensajeria.exchange.eventos}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    /** DLX: recibe lo que las colas principales no pudieron entregar. */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlx, true, false);
    }

    // ───────── Colas principales: durables, con DLX, TTL y largo máximo ─────────

    @Bean
    public Queue notificacionesQueue(@Value("${salon.mensajeria.colas.notificaciones}") String nombre,
            @Value("${salon.mensajeria.dlx.routing.notificaciones}") String routingDlx) {
        return colaPrincipal(nombre, routingDlx);
    }

    @Bean
    public Queue comprobantesQueue(@Value("${salon.mensajeria.colas.comprobantes}") String nombre,
            @Value("${salon.mensajeria.dlx.routing.comprobantes}") String routingDlx) {
        return colaPrincipal(nombre, routingDlx);
    }

    @Bean
    public Queue aprobacionesQueue(@Value("${salon.mensajeria.colas.aprobaciones}") String nombre,
            @Value("${salon.mensajeria.dlx.routing.aprobaciones}") String routingDlx) {
        return colaPrincipal(nombre, routingDlx);
    }

    private Queue colaPrincipal(String nombre, String routingDlx) {
        return QueueBuilder.durable(nombre)
                .deadLetterExchange(dlx) // x-dead-letter-exchange
                .deadLetterRoutingKey(routingDlx) // x-dead-letter-routing-key
                .ttl(ttlColaMs) // x-message-ttl
                .maxLength(maxLength) // x-max-length
                .build();
    }

    // ───────── DLQ: durables, guardan los fallos 24 h ─────────

    @Bean
    public Queue notificacionesDlq(@Value("${salon.mensajeria.dlq.notificaciones}") String nombre) {
        return QueueBuilder.durable(nombre).ttl(ttlDlqMs).build();
    }

    @Bean
    public Queue comprobantesDlq(@Value("${salon.mensajeria.dlq.comprobantes}") String nombre) {
        return QueueBuilder.durable(nombre).ttl(ttlDlqMs).build();
    }

    @Bean
    public Queue aprobacionesDlq(@Value("${salon.mensajeria.dlq.aprobaciones}") String nombre) {
        return QueueBuilder.durable(nombre).ttl(ttlDlqMs).build();
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

    @Bean
    public Declarables bindingsDlq(DirectExchange deadLetterExchange,
            Queue notificacionesDlq,
            Queue comprobantesDlq,
            Queue aprobacionesDlq,
            @Value("${salon.mensajeria.dlx.routing.notificaciones}") String notificaciones,
            @Value("${salon.mensajeria.dlx.routing.comprobantes}") String comprobantes,
            @Value("${salon.mensajeria.dlx.routing.aprobaciones}") String aprobaciones) {
        return new Declarables(
                BindingBuilder.bind(notificacionesDlq).to(deadLetterExchange).with(notificaciones),
                BindingBuilder.bind(comprobantesDlq).to(deadLetterExchange).with(comprobantes),
                BindingBuilder.bind(aprobacionesDlq).to(deadLetterExchange).with(aprobaciones));
    }

    // ───────── Consumo ─────────

    /**
     * Factory de los consumidores de citas:
     *  - JSON → CitaEvento.
     *  - ACK MANUAL: el consumidor decide basicAck / basicNack (ver ConsumoConAck).
     *  - prefetch 1: no recibe el siguiente mensaje hasta resolver el actual (también mientras reintenta).
     *  - Un JSON que no se puede convertir ni siquiera llega al consumidor: Spring AMQP lo rechaza
     *    sin reencolar (ConditionalRejectingErrorHandler, también en modo MANUAL) y, como la cola
     *    tiene DLX, termina en su DLQ en vez de perderse.
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

    /**
     * Factory del consumidor de las DLQ. NO convierte JSON (SimpleMessageConverter entrega los
     * bytes tal cual): a la DLQ llegan justamente mensajes que pueden no ser JSON válido. Con el
     * conversor JSON, Spring AMQP volvía a fallar al convertirlos y, como traen x-death, los
     * descartaba sin ACK: quedaban colgados como unacked.
     * ACK manual: el DlqConsumidor confirma explícitamente tras registrar el fallo.
     */
    @Bean(FACTORY_DLQ)
    public SimpleRabbitListenerContainerFactory dlqListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        return factory;
    }
}
