package com.belleza.rabbitadmin.exception;

/**
 * La operación choca con el estado actual → 409. Por ejemplo: el recurso ya existe con otros
 * argumentos (PRECONDITION_FAILED), está protegido, o la cola no está vacía.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
