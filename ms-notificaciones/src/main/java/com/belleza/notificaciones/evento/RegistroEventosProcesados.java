package com.belleza.notificaciones.evento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotencia: recuerda qué eventoId ya se procesó en cada cola.
 *
 * Con ACK manual la entrega es at-least-once: si el consumidor se cae después de
 * procesar pero antes del basicAck, RabbitMQ reentrega el mismo mensaje. Por eso
 * se verifica el eventoId ANTES de procesar.
 *
 * Es por cola porque un mismo evento llega legítimamente a varias colas
 * (por ejemplo, cita.aprobada a notificaciones Y a comprobantes).
 *
 * LIMITACIÓN: vive en memoria. Se pierde al reiniciar el microservicio y no se
 * comparte entre réplicas. Además es acotado (LRU): un duplicado muy antiguo
 * podría no detectarse. En producción iría en una base de datos (tabla con
 * eventoId único) o en Redis (SET con TTL).
 */
@Component
public class RegistroEventosProcesados {

    private final int maxIdsPorCola;
    private final Map<String, Map<UUID, Boolean>> porCola = new ConcurrentHashMap<>();

    public RegistroEventosProcesados(
            @Value("${salon.mensajeria.idempotencia.max-ids-por-cola:10000}") int maxIdsPorCola) {
        this.maxIdsPorCola = maxIdsPorCola;
    }

    public boolean yaProcesado(String cola, UUID eventoId) {
        Map<UUID, Boolean> ids = idsDe(cola);
        synchronized (ids) {
            return ids.containsKey(eventoId);
        }
    }

    public void marcarProcesado(String cola, UUID eventoId) {
        Map<UUID, Boolean> ids = idsDe(cola);
        synchronized (ids) {
            ids.put(eventoId, Boolean.TRUE);
        }
    }

    /** LinkedHashMap en orden de acceso: al pasar el máximo, se olvida el menos usado (LRU). */
    private Map<UUID, Boolean> idsDe(String cola) {
        return porCola.computeIfAbsent(cola, c -> new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> masAntiguo) {
                return size() > maxIdsPorCola;
            }
        });
    }
}
