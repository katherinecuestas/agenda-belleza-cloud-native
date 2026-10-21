package com.belleza.reservas.controller;

import com.belleza.reservas.dto.CitaRequest;
import com.belleza.reservas.dto.CitaResponse;
import com.belleza.reservas.service.CitaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponse crear(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CitaRequest req) {
        return citaService.crear(jwt.getSubject(), req);
    }

    @GetMapping
    public List<CitaResponse> listarMias(@AuthenticationPrincipal Jwt jwt) {
        return citaService.listarMias(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public CitaResponse obtener(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return citaService.obtenerMia(id, jwt.getSubject());
    }

    @PutMapping("/{id}")
    public CitaResponse actualizar(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody CitaRequest req) {
        return citaService.actualizar(id, jwt.getSubject(), req);
    }

    @DeleteMapping("/{id}")
    public CitaResponse cancelar(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return citaService.cancelar(id, jwt.getSubject());
    }
}
