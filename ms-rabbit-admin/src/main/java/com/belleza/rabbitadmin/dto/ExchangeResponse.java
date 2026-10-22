package com.belleza.rabbitadmin.dto;

/** Exchange declarado en el broker. */
public record ExchangeResponse(String nombre, TipoExchange tipo, boolean durable) {
}
