package com.belleza.reservas.repository;

import com.belleza.reservas.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {
}