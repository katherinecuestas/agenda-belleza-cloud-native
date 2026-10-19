package com.belleza.reservas.dto;

import com.belleza.reservas.model.Especialista;

public record EspecialistaResponse(
        Long id,
        String nombre,
        String email,
        String especialidad,
        Boolean activo) {
    public static EspecialistaResponse from(Especialista e) {
        return new EspecialistaResponse(e.getId(), e.getNombre(), e.getEmail(),
                e.getEspecialidad(), e.getActivo());
    }
}