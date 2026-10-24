package com.belleza.rabbitadmin.controller;

import com.belleza.rabbitadmin.dto.*;
import com.belleza.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller para administrar RabbitMQ
 *
 * Valida los datos (@Valid) y llama a RabbitAdminService
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final RabbitAdminService servicio;

    public AdminController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    /** Crear una cola (201) */
    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public ColaInfoResponse crearCola(@Valid @RequestBody CrearColaRequest req) {
        return servicio.crearCola(req);
    }

    /** Ver una cola: mensajes y consumidores (200, o 404 si no existe) */
    @GetMapping("/queues/{nombre}")
    public ColaInfoResponse infoCola(@PathVariable @NombreRabbit String nombre) {
        return servicio.infoCola(nombre);
    }

    /** Borrar una cola (204); por defecto solo si está vacía */
    @DeleteMapping("/queues/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCola(@PathVariable @NombreRabbit String nombre,
            @RequestParam(defaultValue = "true") boolean soloSiVacia) {
        servicio.eliminarCola(nombre, soloSiVacia);
    }

    /** Vaciar una cola: borra sus mensajes (200 con la cantidad) */
    @PostMapping("/queues/{nombre}/purge")
    public PurgaResponse purgarCola(@PathVariable @NombreRabbit String nombre) {
        return servicio.purgarCola(nombre);
    }

    /** Crear un exchange (201) */
    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeResponse crearExchange(@Valid @RequestBody CrearExchangeRequest req) {
        return servicio.crearExchange(req);
    }

    /** Borrar un exchange (204) */
    @DeleteMapping("/exchanges/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarExchange(@PathVariable @NombreRabbit String nombre) {
        servicio.eliminarExchange(nombre);
    }

    /** Enlazar un exchange con una cola (201) */
    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public BindingRequest crearBinding(@Valid @RequestBody BindingRequest req) {
        return servicio.crearBinding(req);
    }

    /** Quitar un enlace (204); exchange, cola y routingKey van en el body */
    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBinding(@Valid @RequestBody BindingRequest req) {
        servicio.eliminarBinding(req);
    }
}
