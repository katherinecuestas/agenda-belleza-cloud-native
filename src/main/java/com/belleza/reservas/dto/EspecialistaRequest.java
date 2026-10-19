package com.belleza.reservas.dto;

import jakarta.validation.constraints.*;

public record EspecialistaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 100) String especialidad) {
}