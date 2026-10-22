package com.belleza.rabbitadmin.exception;

/** La cola o el exchange no existe en el broker → 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
