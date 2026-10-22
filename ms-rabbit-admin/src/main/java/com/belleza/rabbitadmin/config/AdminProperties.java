package com.belleza.rabbitadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * Propiedades salon.admin.* de application.yml.
 *
 * @param protegidos colas y exchanges del sistema de reservas que esta API no deja borrar
 */
@ConfigurationProperties(prefix = "salon.admin")
public record AdminProperties(List<String> protegidos) {

    public AdminProperties {
        protegidos = protegidos == null ? List.of() : List.copyOf(protegidos);
    }

    public boolean esProtegido(String nombre) {
        return Set.copyOf(protegidos).contains(nombre);
    }
}
