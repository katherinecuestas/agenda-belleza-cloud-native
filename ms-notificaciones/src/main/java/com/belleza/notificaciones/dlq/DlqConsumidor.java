package com.belleza.notificaciones.dlq;

import com.belleza.notificaciones.config.CitasRabbitConfig;
import com.belleza.notificaciones.evento.Ascii;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Registra los mensajes que llegan a las 3 DLQ y los confirma (ACK).
 *
 * La DLQ NO reprocesa: deja constancia del fallo en el log. El mensaje original ya quedó
 * guardado en la DLQ hasta que se consume aquí o vence su TTL (24 h).
 *
 * Recibe el Message crudo (no CitaEvento) a propósito: así también puede registrar
 * los mensajes que llegaron aquí justamente porque su JSON no se podía convertir.
 */
@Component
public class DlqConsumidor {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumidor.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @RabbitListener(queues = {
            "${salon.mensajeria.dlq.notificaciones}",
            "${salon.mensajeria.dlq.comprobantes}",
            "${salon.mensajeria.dlq.aprobaciones}" },
            containerFactory = CitasRabbitConfig.FACTORY_DLQ)
    public void registrar(Message mensaje, Channel channel) throws IOException {
        MessageProperties props = mensaje.getMessageProperties();

        // x-death lo agrega RabbitMQ al hacer dead-lettering: el primero es el más reciente
        Map<String, ?> muerte = primeraMuerte(props.getXDeathHeader());
        Object colaOrigen = muerte.get("queue");
        Object motivo = muerte.get("reason"); // rejected | expired | maxlen
        Object veces = muerte.get("count"); // cuántas veces llegó a dead-letter desde esa cola

        JsonNode cuerpo = leerJson(mensaje.getBody());
        String eventoId = props.getMessageId() != null ? props.getMessageId() : texto(cuerpo, "eventoId");
        String citaId = texto(cuerpo, "citaId");

        log.error("[DLQ] cola={} eventoId={} cita={} motivo={} intentos={} (dlq={})",
                Ascii.de(colaOrigen), Ascii.de(eventoId), Ascii.de(citaId), Ascii.de(motivo), Ascii.de(veces),
                props.getConsumerQueue());

        channel.basicAck(props.getDeliveryTag(), false);
    }

    private static Map<String, ?> primeraMuerte(List<Map<String, ?>> xDeath) {
        return xDeath == null || xDeath.isEmpty() ? Map.of() : xDeath.get(0);
    }

    /** Si el cuerpo no es JSON válido devuelve null: igual se registra el fallo. */
    private static JsonNode leerJson(byte[] cuerpo) {
        try {
            return JSON.readTree(new String(cuerpo, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        if (nodo == null || nodo.path(campo).isMissingNode() || nodo.path(campo).isNull()) {
            return "-";
        }
        return nodo.path(campo).asString();
    }
}
