package com.belleza.reservas.dto;

import jakarta.validation.constraints.*;

public record ResolucionRequest(
        @NotNull Boolean aprobar,
        @Size(max = 500) String comentario) {
}