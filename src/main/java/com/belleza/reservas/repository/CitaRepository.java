package com.belleza.reservas.repository;

import com.belleza.reservas.model.Cita;
import com.belleza.reservas.model.enums.EstadoCita; // ajusta si usaste model.enums
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    List<Cita> findByClienteIdOrderByInicioDesc(String clienteId);

    List<Cita> findByEstadoOrderByInicioAsc(EstadoCita estado);

    @Query("""
            SELECT COUNT(c) > 0 FROM Cita c
            WHERE c.especialista.id = :especialistaId
              AND c.estado IN :estadosActivos
              AND c.inicio < :fin
              AND c.fin > :inicio
              AND (:idExcluir IS NULL OR c.id <> :idExcluir)
            """)
    boolean existeSolapamiento(@Param("especialistaId") Long especialistaId,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin,
            @Param("estadosActivos") Collection<EstadoCita> estadosActivos,
            @Param("idExcluir") Long idExcluir);
}