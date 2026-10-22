package com.belleza.notificaciones.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de los eventos de citas (guía 2.1.3): un DirectExchange, dos colas
 * y bindings por routing key exacta.
 *
 * <pre>
 *                          cita.creada ──────┬──► salon.notificaciones  (las 4 keys)
 * salon.citas.directo ───  cita.aprobada ────┤
 *   (direct)               cita.rechazada ───┤
 *                          cita.cancelada ───┘
 *                          cita.creada ─────────► salon.aprobaciones    (solo creada)
 * </pre>
 */
@Configuration
public class CitasRabbitConfig {

    /** Nombre del container factory que usan los consumidores de citas. */
    public static final String FACTORY_CITAS = "citasListenerFactory";

    /** Misma declaración que ms-reservas (durable, sin auto-delete): si difiere, RabbitMQ la rechaza. */
    @Bean
    public DirectExchange citasExchange(@Value("${salon.mensajeria.exchange.citas}") String nombre) {
        return new DirectExchange(nombre, true, false);
    }

    @Bean
    public Queue notificacionesQueue(@Value("${salon.mensajeria.colas.notificaciones}") String nombre) {
        return new Queue(nombre, true);
    }

    @Bean
    public Queue aprobacionesQueue(@Value("${salon.mensajeria.colas.aprobaciones}") String nombre) {
        return new Queue(nombre, true);
    }

    @Bean
    public Declarables bindingsCitas(DirectExchange citasExchange,
            Queue notificacionesQueue,
            Queue aprobacionesQueue,
            @Value("${salon.mensajeria.routing.creada}") String creada,
            @Value("${salon.mensajeria.routing.aprobada}") String aprobada,
            @Value("${salon.mensajeria.routing.rechazada}") String rechazada,
            @Value("${salon.mensajeria.routing.cancelada}") String cancelada) {
        return new Declarables(
                // salon.notificaciones: la clienta se entera de todo lo que pasa con su cita
                enlazar(notificacionesQueue, citasExchange, creada),
                enlazar(notificacionesQueue, citasExchange, aprobada),
                enlazar(notificacionesQueue, citasExchange, rechazada),
                enlazar(notificacionesQueue, citasExchange, cancelada),
                // salon.aprobaciones: a la aprobadora solo le interesan las citas nuevas
                enlazar(aprobacionesQueue, citasExchange, creada));
    }

    /**
     * Factory propio para los eventos de citas: convierte el JSON a CitaEvento.
     * No se registra como conversor global para no cambiar el ejemplo de saludos (texto plano).
     */
    @Bean(FACTORY_CITAS)
    public SimpleRabbitListenerContainerFactory citasListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new JacksonJsonMessageConverter());
        return factory;
    }

    private static Binding enlazar(Queue cola, DirectExchange exchange, String routingKey) {
        return BindingBuilder.bind(cola).to(exchange).with(routingKey);
    }
}
