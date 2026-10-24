package com.belleza.notificaciones.saludo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller para enviar mensajes a RabbitMQ
 *
 * Basado en:
 * https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html
 */
@RestController
@RequestMapping("/v1/saludos")
public class SaludoController {

    /**
     * Clase DTO para recibir el mensaje en JSON
     */
    public record SaludoRequest(@NotBlank String mensaje) {
    }

    private final SaludoProductor productor;

    public SaludoController(SaludoProductor productor) {
        this.productor = productor;
    }

    /**
     * Enviar un mensaje via POST
     *
     * Ejemplo:
     * curl -X POST http://localhost:8081/v1/saludos \
     *   -H "Content-Type: application/json" \
     *   -d '{"mensaje":"Hola desde REST API!"}'
     *
     * 202 Accepted: el mensaje quedó en la cola y se procesa después
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviar(@Valid @RequestBody SaludoRequest req) {
        productor.enviar(req.mensaje());
    }
}
