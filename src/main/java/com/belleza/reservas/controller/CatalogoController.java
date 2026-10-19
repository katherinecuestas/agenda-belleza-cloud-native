package com.belleza.reservas.controller;

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
    public List<Servicio> servicios() {
        return servicioRepository.findAll();
    }

    @PostMapping("/servicios")
    @ResponseStatus(HttpStatus.CREATED)
    public Servicio crearServicio(@Valid @RequestBody Servicio servicio) {
        servicio.setId(null);
        return servicioRepository.save(servicio);
    }

    @GetMapping("/especialistas")
    public List<Especialista> especialistas() {
        return especialistaRepository.findAll();
    }

    @PostMapping("/especialistas")
    @ResponseStatus(HttpStatus.CREATED)
    public Especialista crearEspecialista(@Valid @RequestBody Especialista especialista) {
        especialista.setId(null);
        return especialistaRepository.save(especialista);
    }
}