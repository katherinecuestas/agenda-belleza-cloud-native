package com.belleza.notificaciones.saludo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/saludos")
public class SaludoController {

    public record SaludoRequest(@NotBlank String mensaje) {
    }

    private final SaludoProductor productor;

    public SaludoController(SaludoProductor productor) {
        this.productor = productor;
    }

    /** 202: el mensaje quedó en la cola; se procesa después, de forma asíncrona. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviar(@Valid @RequestBody SaludoRequest req) {
        productor.enviar(req.mensaje());
    }
}
