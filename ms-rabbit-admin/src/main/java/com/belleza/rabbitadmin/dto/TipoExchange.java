package com.belleza.rabbitadmin.dto;

/** Cómo el exchange decide a qué colas va cada mensaje. */
public enum TipoExchange {
    /** routing key exacta */
    DIRECT,
    /** patrones con * (una palabra) y # (cero o más palabras) */
    TOPIC,
    /** a todas las colas enlazadas, ignora la routing key */
    FANOUT,
    /** según los headers del mensaje */
    HEADERS
}
