package com.belleza.notificaciones.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Copia local del evento que publica ms-reservas (mismo JSON, otro paquete).
 * tipo y estado van como texto: un valor nuevo en el productor no rompe al consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CitaEvento(
        UUID eventoId,
        String tipo,
        Long citaId,
        String clienteId,
        String servicio,
        String especialista,
        Instant inicio,
        String estado,
        String comentarioAprobador,
        Instant ocurridoEn) {
}
