package com.belleza.rabbitadmin.dto;

/** Resultado de vaciar una cola. */
public record PurgaResponse(String cola, int mensajesEliminados) {
}
