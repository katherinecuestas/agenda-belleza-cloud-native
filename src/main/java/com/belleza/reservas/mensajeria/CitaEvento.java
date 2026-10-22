package com.belleza.reservas.mensajeria;

import com.belleza.reservas.model.Cita;
import com.belleza.reservas.model.enums.EstadoCita;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio de una cita. Se publica primero como evento de Spring y,
 * tras el commit, viaja a RabbitMQ como JSON.
 *
 * @param eventoId id único del evento: permite a los consumidores descartar duplicados (idempotencia)
 */
public record CitaEvento(
        UUID eventoId,
        TipoEventoCita tipo,
        Long citaId,
        String clienteId,
        String servicio,
        String especialista,
        Instant inicio,
        EstadoCita estado,
        String comentarioAprobador,
        Instant ocurridoEn) {

    /** Copia los datos de la cita en el momento del cambio (dentro de la transacción). */
    public static CitaEvento de(TipoEventoCita tipo, Cita cita) {
        return new CitaEvento(
                UUID.randomUUID(),
                tipo,
                cita.getId(),
                cita.getClienteId(),
                cita.getServicio().getNombre(),
                cita.getEspecialista().getNombre(),
                cita.getInicio(),
                cita.getEstado(),
                cita.getComentarioAprobador(),
                Instant.now());
    }
}
