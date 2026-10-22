package com.belleza.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Pedido para crear un exchange.
 *
 * @param nombre  obligatorio
 * @param tipo    DIRECT, TOPIC, FANOUT o HEADERS (obligatorio)
 * @param durable si no viene, true
 */
public record CrearExchangeRequest(
        @NotBlank(message = "es obligatorio") @NombreRabbit String nombre,
        @NotNull(message = "es obligatorio: DIRECT, TOPIC, FANOUT o HEADERS") TipoExchange tipo,
        Boolean durable) {

    public CrearExchangeRequest {
        durable = durable == null ? Boolean.TRUE : durable;
    }
}
