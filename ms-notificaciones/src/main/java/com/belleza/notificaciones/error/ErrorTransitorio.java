package com.belleza.notificaciones.error;

/**
 * Error RECUPERABLE: la causa es pasajera y es razonable volver a intentar.
 * Ejemplos: el servicio de correo o SMS no responde, timeout, la base de datos está ocupada.
 *
 * Decisión: reintentar en el mismo consumidor con backoff exponencial; si se agotan
 * los reintentos, NACK sin reencolar → DLQ.
 */
public class ErrorTransitorio extends RuntimeException {

    public ErrorTransitorio(String mensaje) {
        super(mensaje);
    }

    public ErrorTransitorio(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
