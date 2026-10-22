package com.belleza.rabbitadmin.dto;

/**
 * CLASSIC: la cola de siempre, en un solo nodo.
 * QUORUM: replicada (Raft), recomendada por RabbitMQ para datos importantes; siempre durable.
 */
public enum TipoCola {
    CLASSIC,
    QUORUM
}
