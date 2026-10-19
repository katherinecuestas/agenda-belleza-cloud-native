package com.belleza.reservas.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ServicioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 500) String descripcion,
        @NotNull @Positive Integer duracionMinutos,
        @NotNull @PositiveOrZero BigDecimal precio) {
}
