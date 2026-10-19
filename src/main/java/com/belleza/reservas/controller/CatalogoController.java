package com.belleza.reservas.controller;

import com.belleza.reservas.dto.EspecialistaRequest;
import com.belleza.reservas.dto.EspecialistaResponse;
import com.belleza.reservas.dto.EstadoRequest;
import com.belleza.reservas.dto.ServicioRequest;
import com.belleza.reservas.dto.ServicioResponse;
import com.belleza.reservas.service.CatalogoService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/servicios")
    public List<ServicioResponse> servicios() {
        return catalogoService.listarServicios();
    }

    @GetMapping("/servicios/{id}")
    public ServicioResponse servicio(@PathVariable Long id) {
        return catalogoService.obtenerServicio(id);
    }

    @PostMapping("/servicios")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioResponse crearServicio(@Valid @RequestBody ServicioRequest req) {
        return catalogoService.crearServicio(req);
    }

    @PutMapping("/servicios/{id}")
    public ServicioResponse actualizarServicio(@PathVariable Long id,
            @Valid @RequestBody ServicioRequest req) {
        return catalogoService.actualizarServicio(id, req);
    }

    @PatchMapping("/servicios/{id}/estado")
    public ServicioResponse estadoServicio(@PathVariable Long id,
            @Valid @RequestBody EstadoRequest req) {
        return catalogoService.cambiarEstadoServicio(id, req);
    }

    @GetMapping("/especialistas")
    public List<EspecialistaResponse> especialistas() {
        return catalogoService.listarEspecialistas();
    }

    @GetMapping("/especialistas/{id}")
    public EspecialistaResponse especialista(@PathVariable Long id) {
        return catalogoService.obtenerEspecialista(id);
    }

    @PostMapping("/especialistas")
    @ResponseStatus(HttpStatus.CREATED)
    public EspecialistaResponse crearEspecialista(@Valid @RequestBody EspecialistaRequest req) {
        return catalogoService.crearEspecialista(req);
    }

    @PutMapping("/especialistas/{id}")
    public EspecialistaResponse actualizarEspecialista(@PathVariable Long id,
            @Valid @RequestBody EspecialistaRequest req) {
        return catalogoService.actualizarEspecialista(id, req);
    }

    @PatchMapping("/especialistas/{id}/estado")
    public EspecialistaResponse estadoEspecialista(@PathVariable Long id,
            @Valid @RequestBody EstadoRequest req) {
        return catalogoService.cambiarEstadoEspecialista(id, req);
    }
}
