package com.belleza.rabbitadmin.service;

import com.belleza.rabbitadmin.config.AdminProperties;
import com.belleza.rabbitadmin.dto.*;
import com.belleza.rabbitadmin.exception.BrokerNoDisponibleException;
import com.belleza.rabbitadmin.exception.ConflictoException;
import com.belleza.rabbitadmin.exception.ErrorBrokerException;
import com.belleza.rabbitadmin.exception.RecursoNoEncontradoException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Toda la interacción con RabbitMQ pasa por aquí (guía 2.3.1). El controller no conoce
 * RabbitAdmin, ni los tipos de Spring AMQP, ni los códigos del protocolo.
 *
 * Errores del broker → excepciones propias:
 *  - sin conexión                         → BrokerNoDisponibleException (503)
 *  - 404 NOT_FOUND                        → RecursoNoEncontradoException (404)
 *  - 406 PRECONDITION_FAILED, 405 LOCKED  → ConflictoException (409)
 *  - otro código del broker (ej. 540)     → ErrorBrokerException (502)
 *
 * Declarar es idempotente: crear algo que ya existe con la MISMA configuración no falla.
 * Si existe con OTRA configuración, RabbitMQ responde PRECONDITION_FAILED → 409.
 */
@Service
public class RabbitAdminService {

    private static final int AMQP_NOT_FOUND = 404;
    private static final int AMQP_RESOURCE_LOCKED = 405;
    private static final int AMQP_PRECONDITION_FAILED = 406;

    private final RabbitAdmin admin;
    private final AdminProperties propiedades;

    public RabbitAdminService(RabbitAdmin admin, AdminProperties propiedades) {
        this.admin = admin;
        this.propiedades = propiedades;
    }

    // ───────────── Colas ─────────────

    public ColaInfoResponse crearCola(CrearColaRequest req) {
        QueueBuilder builder = req.durable()
                ? QueueBuilder.durable(req.nombre())
                : QueueBuilder.nonDurable(req.nombre());
        if (req.tipo() == TipoCola.QUORUM) {
            builder.quorum();
        }
        if (req.dlx() != null) {
            builder.deadLetterExchange(req.dlx());
        }
        if (req.dlxRoutingKey() != null) {
            builder.deadLetterRoutingKey(req.dlxRoutingKey());
        }
        if (req.ttlMs() != null) {
            builder.ttl(req.ttlMs());
        }
        if (req.maxLength() != null) {
            builder.maxLength(req.maxLength());
        }
        Queue cola = builder.build();

        ejecutar("crear la cola '" + req.nombre() + "'", () -> admin.declareQueue(cola));
        return infoCola(req.nombre());
    }

    public ColaInfoResponse infoCola(String nombre) {
        QueueInformation info = ejecutar("consultar la cola '" + nombre + "'", () -> admin.getQueueInfo(nombre));
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
        return new ColaInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    /**
     * @param soloSiVacia si es true y la cola tiene mensajes, no se borra (409)
     *
     * "Solo si está vacía" se comprueba aquí y NO con el flag if-empty de AMQP: las colas
     * QUORUM no lo soportan (RabbitMQ responde 540 NOT_IMPLEMENTED y cierra la conexión).
     * Limitación: si llega un mensaje justo entre la comprobación y el borrado, se pierde.
     */
    public void eliminarCola(String nombre, boolean soloSiVacia) {
        exigirNoProtegido(nombre, "borrar");
        ColaInfoResponse info = infoCola(nombre);
        if (soloSiVacia && info.mensajes() > 0) {
            throw new ConflictoException("La cola '" + nombre + "' tiene " + info.mensajes()
                    + " mensajes: vacíala (purge) o usa soloSiVacia=false");
        }
        ejecutar("borrar la cola '" + nombre + "'", () -> admin.deleteQueue(nombre));
    }

    /** @return cuántos mensajes se eliminaron */
    public PurgaResponse purgarCola(String nombre) {
        infoCola(nombre); // 404 si no existe
        int eliminados = ejecutar("vaciar la cola '" + nombre + "'", () -> admin.purgeQueue(nombre));
        return new PurgaResponse(nombre, eliminados);
    }

    // ───────────── Exchanges ─────────────

    public ExchangeResponse crearExchange(CrearExchangeRequest req) {
        boolean durable = req.durable();
        Exchange exchange = switch (req.tipo()) {
            case DIRECT -> new DirectExchange(req.nombre(), durable, false);
            case TOPIC -> new TopicExchange(req.nombre(), durable, false);
            case FANOUT -> new FanoutExchange(req.nombre(), durable, false);
            case HEADERS -> new HeadersExchange(req.nombre(), durable, false);
        };
        ejecutar("crear el exchange '" + req.nombre() + "'", () -> {
            admin.declareExchange(exchange);
            return null;
        });
        return new ExchangeResponse(req.nombre(), req.tipo(), durable);
    }

    public void eliminarExchange(String nombre) {
        exigirNoProtegido(nombre, "borrar");
        exigirExchange(nombre);
        ejecutar("borrar el exchange '" + nombre + "'", () -> admin.deleteExchange(nombre));
    }

    // ───────────── Bindings ─────────────

    public BindingRequest crearBinding(BindingRequest req) {
        // Se comprueban antes para responder 404 con un mensaje que diga CUÁL falta
        exigirExchange(req.exchange());
        infoCola(req.cola());
        ejecutar("enlazar '" + req.exchange() + "' con '" + req.cola() + "'",
                () -> {
                    admin.declareBinding(binding(req));
                    return null;
                });
        return req;
    }

    /** Idempotente: quitar un binding que no existe no es error (así responde RabbitMQ). */
    public void eliminarBinding(BindingRequest req) {
        exigirNoProtegido(req.exchange(), "quitar bindings de");
        exigirNoProtegido(req.cola(), "quitar bindings de");
        exigirExchange(req.exchange());
        infoCola(req.cola());
        ejecutar("quitar el enlace '" + req.exchange() + "' -> '" + req.cola() + "'",
                () -> {
                    admin.removeBinding(binding(req));
                    return null;
                });
    }

    // ───────────── Auxiliares ─────────────

    private static Binding binding(BindingRequest req) {
        return new Binding(req.cola(), Binding.DestinationType.QUEUE, req.exchange(), req.routingKey(), null);
    }

    private void exigirNoProtegido(String nombre, String accion) {
        if (propiedades.esProtegido(nombre)) {
            throw new ConflictoException("'" + nombre + "' es un recurso protegido del sistema de reservas: "
                    + "no se puede " + accion + " desde esta API");
        }
    }

    /** RabbitAdmin no trae "existe el exchange?": se usa una declaración pasiva (no crea nada). */
    private void exigirExchange(String nombre) {
        try {
            admin.getRabbitTemplate().execute(canal -> canal.exchangeDeclarePassive(nombre));
        } catch (AmqpConnectException e) {
            throw new BrokerNoDisponibleException("RabbitMQ no está disponible", e);
        } catch (AmqpException e) {
            CierreCanal cierre = CierreCanal.de(e);
            if (cierre != null && cierre.codigo() == AMQP_NOT_FOUND) {
                throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
            }
            throw e;
        }
    }

    /** Ejecuta una operación del broker y traduce sus errores a excepciones propias. */
    private <T> T ejecutar(String accion, Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (AmqpConnectException e) {
            throw new BrokerNoDisponibleException("RabbitMQ no está disponible: no se pudo " + accion, e);
        } catch (AmqpException e) {
            CierreCanal cierre = CierreCanal.de(e);
            if (cierre == null) {
                throw e;
            }
            switch (cierre.codigo()) {
                case AMQP_NOT_FOUND -> throw new RecursoNoEncontradoException(
                        "No se pudo " + accion + ": " + cierre.texto());
                case AMQP_PRECONDITION_FAILED, AMQP_RESOURCE_LOCKED -> throw new ConflictoException(
                        "No se pudo " + accion + ": " + cierre.texto());
                // Cualquier otro rechazo del broker (por ejemplo 540 NOT_IMPLEMENTED): 502 con su texto
                default -> throw new ErrorBrokerException(
                        "RabbitMQ rechazó la operación (no se pudo " + accion + "): " + cierre.texto(), e);
            }
        }
    }

    /**
     * El broker rechaza una operación cerrando el canal (404, 406...) o, en errores graves
     * como 540 NOT_IMPLEMENTED, la conexión entera. Ambos traen código y texto explicativo.
     */
    private record CierreCanal(int codigo, String texto) {

        static CierreCanal de(Throwable error) {
            for (Throwable t = error; t != null; t = t.getCause()) {
                if (t instanceof ShutdownSignalException senal) {
                    if (senal.getReason() instanceof AMQP.Channel.Close cierre) {
                        return new CierreCanal(cierre.getReplyCode(), cierre.getReplyText());
                    }
                    if (senal.getReason() instanceof AMQP.Connection.Close cierre) {
                        return new CierreCanal(cierre.getReplyCode(), cierre.getReplyText());
                    }
                }
            }
            return null;
        }
    }
}
