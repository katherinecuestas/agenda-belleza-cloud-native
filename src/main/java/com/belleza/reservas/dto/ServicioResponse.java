package com.belleza.reservas.dto;

import com.belleza.reservas.model.Servicio;
import java.math.BigDecimal;

public record ServicioResponse(
        Long id,
        String nombre,
        String descripcion,
        Integer duracionMinutos,
        BigDecimal precio,
        Boolean activo) {
    public static ServicioResponse from(Servicio s) {
        return new ServicioResponse(s.getId(), s.getNombre(), s.getDescripcion(),
                s.getDuracionMinutos(), s.getPrecio(), s.getActivo());
    }
}