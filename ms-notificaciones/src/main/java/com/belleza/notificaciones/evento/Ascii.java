package com.belleza.notificaciones.evento;

import java.text.Normalizer;

/** Texto solo ASCII para los logs: la consola de Windows no muestra bien tildes ni símbolos. */
public final class Ascii {

    private Ascii() {
    }

    /** "Depilación" → "Depilacion"; lo que no tenga equivalente ASCII queda como "?". */
    public static String de(Object valor) {
        if (valor == null) return "-";
        String sinTildes = Normalizer.normalize(valor.toString(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
