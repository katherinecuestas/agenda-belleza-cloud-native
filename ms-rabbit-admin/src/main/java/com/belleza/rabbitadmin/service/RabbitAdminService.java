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
 * Servicio que administra RabbitMQ con RabbitAdmin
 *
 * Crea, consulta y borra colas, exchanges y bindings.
 * Si RabbitMQ responde con error, lo convierte en un error claro:
 * - RabbitMQ apagado -> 503
 * - no existe -> 404
 * - ya existe con otra configuración, protegido o con mensajes -> 409
 * - otro rechazo de RabbitMQ -> 502
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

    /**
     * Crea una cola con su tipo, durable, DLX, TTL y largo máximo.
     * Es idempotente: si ya existe igual, la reutiliza
     */
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

    /** Devuelve los mensajes y consumidores de una cola */
    public ColaInfoResponse infoCola(String nombre) {
        QueueInformation info = ejecutar("consultar la cola '" + nombre + "'", () -> admin.getQueueInfo(nombre));
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
        return new ColaInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    /** Borra una cola; con soloSiVacia=true no la borra si tiene mensajes */
    public void eliminarCola(String nombre, boolean soloSiVacia) {
        exigirNoProtegido(nombre, "borrar");
        ColaInfoResponse info = infoCola(nombre);
        if (soloSiVacia && info.mensajes() > 0) {
            throw new ConflictoException("La cola '" + nombre + "' tiene " + info.mensajes()
                    + " mensajes: vacíala (purge) o usa soloSiVacia=false");
        }
        ejecutar("borrar la cola '" + nombre + "'", () -> admin.deleteQueue(nombre));
    }

    /** Vacía una cola y devuelve cuántos mensajes borró */
    public PurgaResponse purgarCola(String nombre) {
        infoCola(nombre);
        int eliminados = ejecutar("vaciar la cola '" + nombre + "'", () -> admin.purgeQueue(nombre));
        return new PurgaResponse(nombre, eliminados);
    }

    /** Crea un exchange DIRECT, TOPIC, FANOUT o HEADERS */
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

    /** Borra un exchange (no los protegidos del salón) */
    public void eliminarExchange(String nombre) {
        exigirNoProtegido(nombre, "borrar");
        exigirExchange(nombre);
        ejecutar("borrar el exchange '" + nombre + "'", () -> admin.deleteExchange(nombre));
    }

    /** Enlaza un exchange con una cola usando una routing key */
    public BindingRequest crearBinding(BindingRequest req) {
        exigirExchange(req.exchange());
        infoCola(req.cola());
        ejecutar("enlazar '" + req.exchange() + "' con '" + req.cola() + "'",
                () -> {
                    admin.declareBinding(binding(req));
                    return null;
                });
        return req;
    }

    /** Quita el enlace entre un exchange y una cola */
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

    /** Arma el binding exchange -> cola */
    private static Binding binding(BindingRequest req) {
        return new Binding(req.cola(), Binding.DestinationType.QUEUE, req.exchange(), req.routingKey(), null);
    }

    /** Impide tocar las colas y exchanges que usa el sistema de reservas */
    private void exigirNoProtegido(String nombre, String accion) {
        if (propiedades.esProtegido(nombre)) {
            throw new ConflictoException("'" + nombre + "' es un recurso protegido del sistema de reservas: "
                    + "no se puede " + accion + " desde esta API");
        }
    }

    /** Comprueba que el exchange exista (404 si no existe) */
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

    /** Ejecuta una operación en RabbitMQ y convierte sus errores en errores claros */
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
                default -> throw new ErrorBrokerException(
                        "RabbitMQ rechazó la operación (no se pudo " + accion + "): " + cierre.texto(), e);
            }
        }
    }

    /** Código y texto del error que devuelve RabbitMQ (por ejemplo 404 o 406) */
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
