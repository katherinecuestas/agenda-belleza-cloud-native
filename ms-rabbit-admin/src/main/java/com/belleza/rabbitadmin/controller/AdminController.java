package com.belleza.rabbitadmin.controller;

import com.belleza.rabbitadmin.dto.*;
import com.belleza.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * API REST de administración de RabbitMQ. Solo valida la entrada (@Valid y @NombreRabbit)
 * y delega: no conoce RabbitAdmin ni nada del protocolo AMQP.
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final RabbitAdminService servicio;

    public AdminController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    // ───────────── Colas ─────────────

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public ColaInfoResponse crearCola(@Valid @RequestBody CrearColaRequest req) {
        return servicio.crearCola(req);
    }

    @GetMapping("/queues/{nombre}")
    public ColaInfoResponse infoCola(@PathVariable @NombreRabbit String nombre) {
        return servicio.infoCola(nombre);
    }

    /** Por seguridad, por defecto solo borra si la cola está vacía. */
    @DeleteMapping("/queues/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCola(@PathVariable @NombreRabbit String nombre,
            @RequestParam(defaultValue = "true") boolean soloSiVacia) {
        servicio.eliminarCola(nombre, soloSiVacia);
    }

    @PostMapping("/queues/{nombre}/purge")
    public PurgaResponse purgarCola(@PathVariable @NombreRabbit String nombre) {
        return servicio.purgarCola(nombre);
    }

    // ───────────── Exchanges ─────────────

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeResponse crearExchange(@Valid @RequestBody CrearExchangeRequest req) {
        return servicio.crearExchange(req);
    }

    @DeleteMapping("/exchanges/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarExchange(@PathVariable @NombreRabbit String nombre) {
        servicio.eliminarExchange(nombre);
    }

    // ───────────── Bindings ─────────────

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public BindingRequest crearBinding(@Valid @RequestBody BindingRequest req) {
        return servicio.crearBinding(req);
    }

    /** El binding se identifica por exchange + cola + routingKey, por eso va en el body. */
    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBinding(@Valid @RequestBody BindingRequest req) {
        servicio.eliminarBinding(req);
    }
}
