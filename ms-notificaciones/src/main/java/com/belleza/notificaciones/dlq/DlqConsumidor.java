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
 * Consumidor (Receiver) - Recibe mensajes de las colas
 * 'salon.notificaciones.dlq', 'salon.comprobantes.dlq' y 'salon.aprobaciones.dlq'
 *
 * DLQ: registra en el log los mensajes que fallaron y no los vuelve a procesar
 *
 * Basado en:
 * https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
 * https://docs.spring.io/spring-amqp/reference/
 */
@Component
public class DlqConsumidor {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumidor.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Este método se ejecuta automáticamente cada vez que
     * llega un mensaje a una de las 3 DLQ
     *
     * La anotación @RabbitListener:
     * - Registra este método como listener de mensajes
     * - queues={...} especifica las colas a escuchar
     *
     * Parámetros:
     * - mensaje: el mensaje sin convertir, así también se registra un JSON inválido
     * - channel: Acceso al canal AMQP para confirmación manual (manual acks)
     *
     * x-death: header que agrega RabbitMQ con la cola de origen,
     * el motivo (rejected, expired, maxlen) y cuántas veces llegó a la DLQ
     */
    @RabbitListener(queues = {
            "${salon.mensajeria.dlq.notificaciones}",
            "${salon.mensajeria.dlq.comprobantes}",
            "${salon.mensajeria.dlq.aprobaciones}" },
            containerFactory = CitasRabbitConfig.FACTORY_DLQ)
    public void registrar(Message mensaje, Channel channel) throws IOException {
        MessageProperties props = mensaje.getMessageProperties();

        Map<String, ?> muerte = primeraMuerte(props.getXDeathHeader());
        Object colaOrigen = muerte.get("queue");
        Object motivo = muerte.get("reason");
        Object veces = muerte.get("count");

        JsonNode cuerpo = leerJson(mensaje.getBody());
        String eventoId = props.getMessageId() != null ? props.getMessageId() : texto(cuerpo, "eventoId");
        String citaId = texto(cuerpo, "citaId");

        log.error("[DLQ] cola={} eventoId={} cita={} motivo={} intentos={} (dlq={})",
                Ascii.de(colaOrigen), Ascii.de(eventoId), Ascii.de(citaId), Ascii.de(motivo), Ascii.de(veces),
                props.getConsumerQueue());

        // Confirmación manual del mensaje (si usas manual acks)
        // basicAck(): Confirma que el mensaje fue procesado exitosamente
        channel.basicAck(props.getDeliveryTag(), false);
    }

    /** Lee el primer registro del header x-death (el más reciente) */
    private static Map<String, ?> primeraMuerte(List<Map<String, ?>> xDeath) {
        return xDeath == null || xDeath.isEmpty() ? Map.of() : xDeath.get(0);
    }

    /** Lee el cuerpo como JSON; si no es JSON válido devuelve null */
    private static JsonNode leerJson(byte[] cuerpo) {
        try {
            return JSON.readTree(new String(cuerpo, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Lee un campo del JSON; si no existe devuelve "-" */
    private static String texto(JsonNode nodo, String campo) {
        if (nodo == null || nodo.path(campo).isMissingNode() || nodo.path(campo).isNull()) {
            return "-";
        }
        return nodo.path(campo).asString();
    }
}
