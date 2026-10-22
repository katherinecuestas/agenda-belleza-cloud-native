package com.belleza.notificaciones.error;

/**
 * Error NO RECUPERABLE: reintentar daría el mismo resultado.
 * Ejemplos: datos inválidos o incompletos en el evento, una regla de negocio que no se cumple.
 *
 * Decisión: NACK sin reencolar inmediato → DLQ, sin reintentos (reintentar solo gasta tiempo
 * y retiene la cola, porque el prefetch es 1).
 */
public class ErrorPermanente extends RuntimeException {

    public ErrorPermanente(String mensaje) {
        super(mensaje);
    }

    public ErrorPermanente(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
