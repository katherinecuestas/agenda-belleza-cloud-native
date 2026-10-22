package com.belleza.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Enlace exchange → cola.
 *
 * @param routingKey obligatoria, pero puede ser "" (un FANOUT la ignora); en TOPIC admite * y #
 */
public record BindingRequest(
        @NotBlank(message = "es obligatorio") @NombreRabbit String exchange,
        @NotBlank(message = "es obligatorio") @NombreRabbit String cola,
        @NotNull(message = "es obligatoria (puede ser \"\" para un FANOUT)")
        @Size(max = 255, message = "no puede superar 255 caracteres") String routingKey) {
}
