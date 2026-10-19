package com.belleza.reservas.controller;

import com.belleza.reservas.dto.CitaResponse;
import com.belleza.reservas.dto.ResolucionRequest;
import com.belleza.reservas.service.CitaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/aprobaciones")
public class AprobacionController {

    private final CitaService citaService;

    public AprobacionController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping("/pendientes")
    public List<CitaResponse> pendientes() {
        return citaService.listarPendientes();
    }

    @PatchMapping("/{id}")
    public CitaResponse resolver(@PathVariable Long id,
            @Valid @RequestBody ResolucionRequest req) {
        return citaService.resolver(id, req);
    }
}