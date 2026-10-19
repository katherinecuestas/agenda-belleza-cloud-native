package com.belleza.reservas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "servicios")
@Getter
@Setter
@NoArgsConstructor
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Size(max = 500)
    @Column(length = 500)
    private String descripcion;

    @NotNull
    @Positive
    @Column(name = "duracion_minutos", nullable = false)
    private Integer duracionMinutos;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal precio;

    @Column(nullable = false)
    private Boolean activo = true;
}