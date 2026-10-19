package com.belleza.reservas.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CitaRequest(
        @NotNull Long servicioId,
        @NotNull Long especialistaId,
        @NotNull @Future Instant inicio,
        @Size(max = 500) String notasCliente) {
}