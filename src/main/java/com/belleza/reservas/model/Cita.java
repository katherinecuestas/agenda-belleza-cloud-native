package com.belleza.reservas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import com.belleza.reservas.model.enums.EstadoCita;
import java.time.Instant;

@Entity
@Table(name = "citas")
@Getter
@Setter
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialista_id", nullable = false)
    private Especialista especialista;

    @NotBlank
    @Column(name = "cliente_id", nullable = false, length = 100)
    private String clienteId;

    @NotNull
    @Column(name = "inicio", nullable = false)
    private Instant inicio;

    @NotNull
    @Column(name = "fin", nullable = false)
    private Instant fin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCita estado = EstadoCita.PENDIENTE;

    @Size(max = 500)
    @Column(name = "notas_cliente", length = 500)
    private String notasCliente;

    @Size(max = 500)
    @Column(name = "comentario_aprobador", length = 500)
    private String comentarioAprobador;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @PrePersist
    void alCrear() {
        this.creadaEn = Instant.now();
    }
}