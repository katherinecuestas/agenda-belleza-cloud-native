package com.belleza.reservas.mensajeria;

import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * CorrelationData con el eventoId como id, más el destino: así el
 * ConfirmCallback puede decir QUÉ publicación confirmó el broker.
 */
public class CorrelacionEvento extends CorrelationData {

    private final String exchange;
    private final String routingKey;

    public CorrelacionEvento(String eventoId, String exchange, String routingKey) {
        super(eventoId);
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
