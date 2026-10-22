package com.belleza.rabbitadmin.exception;

/** No hay conexión con RabbitMQ → 503. */
public class BrokerNoDisponibleException extends RuntimeException {

    public BrokerNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
