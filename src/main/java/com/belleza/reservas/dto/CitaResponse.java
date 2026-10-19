package com.belleza.reservas.dto;

import com.belleza.reservas.model.Cita;
import com.belleza.reservas.model.enums.EstadoCita;
import java.time.Instant;

public record CitaResponse(
        Long id,
        String servicio,
        String especialista,
        String clienteId,
        Instant inicio,
        Instant fin,
        EstadoCita estado,
        String notasCliente,
        String comentarioAprobador) {
    public static CitaResponse from(Cita c) {
        return new CitaResponse(
                c.getId(),
                c.getServicio().getNombre(),
                c.getEspecialista().getNombre(),
                c.getClienteId(),
                c.getInicio(),
                c.getFin(),
                c.getEstado(),
                c.getNotasCliente(),
                c.getComentarioAprobador());
    }
}