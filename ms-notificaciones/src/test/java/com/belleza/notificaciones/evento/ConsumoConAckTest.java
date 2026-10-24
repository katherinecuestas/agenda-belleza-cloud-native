package com.belleza.notificaciones.evento;

import com.belleza.notificaciones.error.ErrorPermanente;
import com.belleza.notificaciones.error.ErrorTransitorio;
import com.belleza.notificaciones.error.SimuladorFallos;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios de ConsumoConAck con el Channel de RabbitMQ mockeado: no necesitan broker.
 *
 * Los reintentos usan esperas de 1 ms (en producción son 1 s, 2 s y 4 s): el intervalo y el
 * multiplicador llegan por el constructor, así que el test es rápido sin tocar el código.
 */
class ConsumoConAckTest {

    private static final String COLA = "salon.notificaciones";
    private static final long TAG = 7L;
    private static final int MAX_REINTENTOS = 3;

    private Channel channel;
    private ConsumoConAck consumo;

    @BeforeEach
    void preparar() {
        channel = mock(Channel.class);
        consumo = new ConsumoConAck(new RegistroEventosProcesados(100), new SimuladorFallos(),
                MAX_REINTENTOS, 1L, 2.0);
    }

    private static CitaEvento evento(UUID eventoId) {
        return new CitaEvento(eventoId, "APROBADA", 501L, "clienta-123", "Manicure", "Camila",
                Instant.parse("2026-11-01T13:00:00Z"), "APROBADA", "ok", Instant.now());
    }

    @Test
    void procesar_sinErrores_confirmaConBasicAck() throws Exception {
        AtomicInteger ejecuciones = new AtomicInteger();

        consumo.procesar(COLA, evento(UUID.randomUUID()), null, channel, TAG, e -> ejecuciones.incrementAndGet());

        assertEquals(1, ejecuciones.get());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void procesar_conErrorPermanente_haceBasicNackSinReintentar() throws Exception {
        AtomicInteger intentos = new AtomicInteger();

        consumo.procesar(COLA, evento(UUID.randomUUID()), null, channel, TAG, e -> {
            intentos.incrementAndGet();
            throw new ErrorPermanente("datos que nunca van a servir");
        });

        assertEquals(1, intentos.get());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void procesar_conErrorTransitorioQueSiempreFalla_reintentaYTerminaEnBasicNack() throws Exception {
        AtomicInteger intentos = new AtomicInteger();

        consumo.procesar(COLA, evento(UUID.randomUUID()), null, channel, TAG, e -> {
            intentos.incrementAndGet();
            throw new ErrorTransitorio("el servicio de correo no responde");
        });

        // Primer intento + 3 reintentos
        assertEquals(MAX_REINTENTOS + 1, intentos.get());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void procesar_conEventoIdRepetido_confirmaSinProcesarDeNuevo() throws Exception {
        UUID mismoEventoId = UUID.randomUUID();
        AtomicInteger ejecuciones = new AtomicInteger();

        consumo.procesar(COLA, evento(mismoEventoId), null, channel, TAG, e -> ejecuciones.incrementAndGet());
        consumo.procesar(COLA, evento(mismoEventoId), null, channel, TAG + 1, e -> ejecuciones.incrementAndGet());

        assertEquals(1, ejecuciones.get());
        verify(channel).basicAck(TAG, false);
        verify(channel).basicAck(TAG + 1, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
