package com.belleza.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Pedido para crear una cola.
 *
 * @param nombre        obligatorio
 * @param tipo          CLASSIC o QUORUM; si no viene, QUORUM
 * @param durable       si no viene, true; una QUORUM siempre es durable
 * @param dlx           exchange al que van los mensajes rechazados o vencidos (x-dead-letter-exchange)
 * @param dlxRoutingKey routing key con la que llegan al DLX (x-dead-letter-routing-key); requiere dlx
 * @param ttlMs         milisegundos que un mensaje puede esperar en la cola (x-message-ttl)
 * @param maxLength     máximo de mensajes en la cola (x-max-length)
 */
public record CrearColaRequest(
        @NotBlank(message = "es obligatorio") @NombreRabbit String nombre,
        TipoCola tipo,
        Boolean durable,
        @NombreRabbit String dlx,
        @Size(max = 255, message = "no puede superar 255 caracteres") String dlxRoutingKey,
        @Positive(message = "debe ser mayor que 0") Integer ttlMs,
        @Positive(message = "debe ser mayor que 0") Integer maxLength) {

    /** Valores por defecto: tipo QUORUM y durable true. */
    public CrearColaRequest {
        tipo = tipo == null ? TipoCola.QUORUM : tipo;
        durable = durable == null ? Boolean.TRUE : durable;
    }

    @JsonIgnore
    @AssertTrue(message = "una cola QUORUM siempre es durable: envía durable=true o usa tipo CLASSIC")
    public boolean isQuorumDurable() {
        return tipo != TipoCola.QUORUM || Boolean.TRUE.equals(durable);
    }

    @JsonIgnore
    @AssertTrue(message = "dlxRoutingKey solo tiene sentido junto con dlx")
    public boolean isDlxCompleto() {
        return dlxRoutingKey == null || dlx != null;
    }
}
