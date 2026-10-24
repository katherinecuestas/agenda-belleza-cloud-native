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
 * Configuración de RabbitMQ
 *
 * Basada en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Configuration
public class CitasRabbitConfig {

    /** Container factory que usan los consumidores de citas */
    public static final String FACTORY_CITAS = "citasListenerFactory";
    /** Container factory que usa el consumidor de las DLQ */
    public static final String FACTORY_DLQ = "dlqListenerFactory";

    @Value("${salon.mensajeria.dlx.exchange}")
    private String dlx;
    @Value("${salon.mensajeria.limites.ttl-ms}")
    private int ttlColaMs;
    @Value("${salon.mensajeria.limites.max-length}")
    private int maxLength;
    @Value("${salon.mensajeria.dlq.ttl-ms}")
    private int ttlDlqMs;

    /**
     * Define el exchange "salon.citas.directo" (DIRECT)
     *
     * Exchange explícito (para casos más avanzados):
     * la routing key debe coincidir exacta con la del binding
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public DirectExchange citasExchange(@Value("${salon.mensajeria.exchange.citas}") String nombre) {
        return new DirectExchange(nombre, true, false);
    }

    /**
     * Define el exchange "salon.citas.eventos" (TOPIC)
     *
     * Exchange explícito (para casos más avanzados):
     * la routing key se compara con patrones, por ejemplo "cita.*"
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public TopicExchange eventosExchange(@Value("${salon.mensajeria.exchange.eventos}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    /**
     * Define el exchange "salon.citas.dlx" (DIRECT)
     *
     * DLX: recibe los mensajes que las colas principales rechazan,
     * vencen o desbordan, y los manda a su DLQ
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlx, true, false);
    }

    /**
     * Define la cola "salon.notificaciones"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public Queue notificacionesQueue(@Value("${salon.mensajeria.colas.notificaciones}") String nombre,
            @Value("${salon.mensajeria.dlx.routing.notificaciones}") String routingDlx) {
        return colaPrincipal(nombre, routingDlx);
    }

    /**
     * Define la cola "salon.comprobantes"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public Queue comprobantesQueue(@Value("${salon.mensajeria.colas.comprobantes}") String nombre,
            @Value("${salon.mensajeria.dlx.routing.comprobantes}") String routingDlx) {
        return colaPrincipal(nombre, routingDlx);
    }

    /**
     * Define la cola "salon.aprobaciones"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public Queue aprobacionesQueue(@Value("${salon.mensajeria.colas.aprobaciones}") String nombre,
            @Value("${salon.mensajeria.dlx.routing.aprobaciones}") String routingDlx) {
        return colaPrincipal(nombre, routingDlx);
    }

    /**
     * Arma una cola principal:
     * - quorum: replicada en los 3 nodos del cluster
     * - deadLetterExchange / deadLetterRoutingKey: a dónde va un mensaje rechazado
     * - ttl: tiempo máximo que un mensaje espera en la cola
     * - maxLength: cantidad máxima de mensajes en la cola
     */
    private Queue colaPrincipal(String nombre, String routingDlx) {
        return QueueBuilder.durable(nombre)
                .quorum()
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(routingDlx)
                .ttl(ttlColaMs)
                .maxLength(maxLength)
                .build();
    }

    /**
     * Define la cola "salon.notificaciones.dlq"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public Queue notificacionesDlq(@Value("${salon.mensajeria.dlq.notificaciones}") String nombre) {
        return dlq(nombre);
    }

    /**
     * Define la cola "salon.comprobantes.dlq"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public Queue comprobantesDlq(@Value("${salon.mensajeria.dlq.comprobantes}") String nombre) {
        return dlq(nombre);
    }

    /**
     * Define la cola "salon.aprobaciones.dlq"
     *
     * Esta cola es idempotente:
     * - Si no existe, la crea
     * - Si ya existe, la reutiliza
     *
     * durable=true: sobrevive si RabbitMQ se reinicia
     * (En producción, normalmente usarías durable=true)
     */
    @Bean
    public Queue aprobacionesDlq(@Value("${salon.mensajeria.dlq.aprobaciones}") String nombre) {
        return dlq(nombre);
    }

    /**
     * Arma una DLQ:
     * - quorum: replicada en los 3 nodos del cluster
     * - ttl: guarda cada mensaje fallido 24 h
     */
    private Queue dlq(String nombre) {
        return QueueBuilder.durable(nombre).quorum().ttl(ttlDlqMs).build();
    }

    /**
     * Bindings: enlazan cada cola con su exchange y su routing key
     * - salon.notificaciones  <- salon.citas.eventos  con "cita.*" (todos los eventos)
     * - salon.comprobantes    <- salon.citas.eventos  con "cita.aprobada"
     * - salon.aprobaciones    <- salon.citas.directo  con "cita.creada"
     */
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
                BindingBuilder.bind(notificacionesQueue).to(eventosExchange).with(todasLasCitas),
                BindingBuilder.bind(comprobantesQueue).to(eventosExchange).with(aprobada),
                BindingBuilder.bind(aprobacionesQueue).to(citasExchange).with(creada));
    }

    /**
     * Bindings de las DLQ: enlazan cada DLQ con salon.citas.dlx
     * - salon.notificaciones.dlq <- "notificaciones"
     * - salon.comprobantes.dlq   <- "comprobantes"
     * - salon.aprobaciones.dlq   <- "aprobaciones"
     */
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

    /**
     * Listener de los consumidores de citas
     *
     * - JacksonJsonMessageConverter: Spring AMQP deserializa automáticamente el mensaje
     * - acknowledge-mode: manual  # Confirmación manual
     * - prefetch: 1               # Procesar 1 mensaje a la vez
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
     * Listener del consumidor de las DLQ
     *
     * - SimpleMessageConverter: recibe el mensaje sin convertir a JSON,
     *   así también registra los mensajes con JSON inválido
     * - acknowledge-mode: manual  # Confirmación manual
     * - prefetch: 1               # Procesar 1 mensaje a la vez
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
