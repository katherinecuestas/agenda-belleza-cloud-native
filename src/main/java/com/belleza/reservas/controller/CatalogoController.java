package com.belleza.reservas.controller;

import com.belleza.reservas.dto.EspecialistaRequest;
import com.belleza.reservas.dto.EspecialistaResponse;
import com.belleza.reservas.dto.ServicioRequest;
import com.belleza.reservas.dto.ServicioResponse;
import com.belleza.reservas.model.Especialista;
import com.belleza.reservas.model.Servicio;
import com.belleza.reservas.repository.EspecialistaRepository;
import com.belleza.reservas.repository.ServicioRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class CatalogoController {

    private final ServicioRepository servicioRepository;
    private final EspecialistaRepository especialistaRepository;

    public CatalogoController(ServicioRepository servicioRepository,
            EspecialistaRepository especialistaRepository) {
        this.servicioRepository = servicioRepository;
        this.especialistaRepository = especialistaRepository;
    }

    @GetMapping("/servicios")
    public List<ServicioResponse> servicios() {
        return servicioRepository.findAll().stream()
                .map(ServicioResponse::from)
                .toList();
    }

    @PostMapping("/servicios")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioResponse crearServicio(@Valid @RequestBody ServicioRequest req) {
        Servicio s = new Servicio();
        s.setNombre(req.nombre());
        s.setDescripcion(req.descripcion());
        s.setDuracionMinutos(req.duracionMinutos());
        s.setPrecio(req.precio());
        return ServicioResponse.from(servicioRepository.save(s));
    }

    @GetMapping("/especialistas")
    public List<EspecialistaResponse> especialistas() {
        return especialistaRepository.findAll().stream()
                .map(EspecialistaResponse::from)
                .toList();
    }

    @PostMapping("/especialistas")
    @ResponseStatus(HttpStatus.CREATED)
    public EspecialistaResponse crearEspecialista(@Valid @RequestBody EspecialistaRequest req) {
        Especialista e = new Especialista();
        e.setNombre(req.nombre());
        e.setEmail(req.email());
        e.setEspecialidad(req.especialidad());
        return EspecialistaResponse.from(especialistaRepository.save(e));
    }
}