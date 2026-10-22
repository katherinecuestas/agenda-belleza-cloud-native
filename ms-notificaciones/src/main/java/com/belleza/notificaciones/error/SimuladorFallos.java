package com.belleza.notificaciones.error;

import org.springframework.stereotype.Component;

/**
 * SOLO PARA LA DEMO: si el mensaje trae el header x-simular-fallo, el procesamiento
 * falla a propósito con el tipo de error pedido. Sin el header, no hace nada.
 */
@Component
public class SimuladorFallos {

    public static final String HEADER = "x-simular-fallo";

    public void aplicar(String modo) {
        if (modo == null) {
            return;
        }
        switch (modo.trim().toLowerCase()) {
            case "transitorio" -> throw new ErrorTransitorio("fallo simulado: transitorio (header " + HEADER + ")");
            case "permanente" -> throw new ErrorPermanente("fallo simulado: permanente (header " + HEADER + ")");
            default -> {
                // Valor desconocido: se ignora, el mensaje se procesa normal
            }
        }
    }
}
