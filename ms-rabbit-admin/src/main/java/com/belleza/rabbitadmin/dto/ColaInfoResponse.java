package com.belleza.rabbitadmin.dto;

/** Estado de una cola en el broker. */
public record ColaInfoResponse(String nombre, long mensajes, int consumidores) {
}
