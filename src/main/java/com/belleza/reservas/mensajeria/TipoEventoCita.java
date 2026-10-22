package com.belleza.reservas.mensajeria;

/** Lo que le pasó a la cita. Cada tipo viaja con su propia routing key. */
public enum TipoEventoCita {
    CREADA,
    APROBADA,
    RECHAZADA,
    CANCELADA
}
