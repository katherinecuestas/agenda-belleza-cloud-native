package com.belleza.reservas.controller;

import com.belleza.reservas.dto.CitaRequest;
import com.belleza.reservas.dto.CitaResponse;
import com.belleza.reservas.service.CitaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public CitaResponse crear(@RequestHeader("X-Cliente-Id") String clienteId,
            @Valid @RequestBody CitaRequest req) {
        return citaService.crear(clienteId, req);
    }

    @GetMapping
    public List<CitaResponse> listarMias(@RequestHeader("X-Cliente-Id") String clienteId) {
        return citaService.listarMias(clienteId);
    }

    @GetMapping("/{id}")
    public CitaResponse obtener(@RequestHeader("X-Cliente-Id") String clienteId,
            @PathVariable Long id) {
        return citaService.obtenerMia(id, clienteId);
    }

    @PutMapping("/{id}")
    public CitaResponse actualizar(@RequestHeader("X-Cliente-Id") String clienteId,
            @PathVariable Long id,
            @Valid @RequestBody CitaRequest req) {
        return citaService.actualizar(id, clienteId, req);
    }

    @DeleteMapping("/{id}")
    public CitaResponse cancelar(@RequestHeader("X-Cliente-Id") String clienteId,
            @PathVariable Long id) {
        return citaService.cancelar(id, clienteId);
    }
}