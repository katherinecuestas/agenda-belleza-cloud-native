package com.belleza.rabbitadmin.exception;

/**
 * RabbitMQ rechazó la operación con un código que no tiene una respuesta propia
 * (por ejemplo 540 NOT_IMPLEMENTED) → 502, con el texto del broker.
 */
public class ErrorBrokerException extends RuntimeException {

    public ErrorBrokerException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
