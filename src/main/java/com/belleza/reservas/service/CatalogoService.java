package com.belleza.reservas.service;

import com.belleza.reservas.dto.EspecialistaRequest;
import com.belleza.reservas.dto.EspecialistaResponse;
import com.belleza.reservas.dto.EstadoRequest;
import com.belleza.reservas.dto.ServicioRequest;
import com.belleza.reservas.dto.ServicioResponse;
import com.belleza.reservas.exception.RecursoNoEncontradoException;
import com.belleza.reservas.model.Especialista;
import com.belleza.reservas.model.Servicio;
import com.belleza.reservas.repository.EspecialistaRepository;
import com.belleza.reservas.repository.ServicioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogoService {

    private final ServicioRepository servicioRepository;
    private final EspecialistaRepository especialistaRepository;

    public CatalogoService(ServicioRepository servicioRepository,
            EspecialistaRepository especialistaRepository) {
        this.servicioRepository = servicioRepository;
        this.especialistaRepository = especialistaRepository;
    }

    @Transactional(readOnly = true)
    public List<ServicioResponse> listarServicios() {
        return servicioRepository.findAll().stream().map(ServicioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServicioResponse obtenerServicio(Long id) {
        return ServicioResponse.from(buscarServicio(id));
    }

    @Transactional
    public ServicioResponse crearServicio(ServicioRequest req) {
        Servicio s = new Servicio();
        copiar(req, s);
        return ServicioResponse.from(servicioRepository.save(s));
    }

    @Transactional
    public ServicioResponse actualizarServicio(Long id, ServicioRequest req) {
        Servicio s = buscarServicio(id);
        copiar(req, s);
        return ServicioResponse.from(servicioRepository.saveAndFlush(s));
    }

    @Transactional
    public ServicioResponse cambiarEstadoServicio(Long id, EstadoRequest req) {
        Servicio s = buscarServicio(id);
        s.setActivo(req.activo());
        return ServicioResponse.from(s);
    }

    @Transactional(readOnly = true)
    public List<EspecialistaResponse> listarEspecialistas() {
        return especialistaRepository.findAll().stream().map(EspecialistaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EspecialistaResponse obtenerEspecialista(Long id) {
        return EspecialistaResponse.from(buscarEspecialista(id));
    }

    @Transactional
    public EspecialistaResponse crearEspecialista(EspecialistaRequest req) {
        Especialista e = new Especialista();
        copiar(req, e);
        return EspecialistaResponse.from(especialistaRepository.save(e));
    }

    @Transactional
    public EspecialistaResponse actualizarEspecialista(Long id, EspecialistaRequest req) {
        Especialista e = buscarEspecialista(id);
        copiar(req, e);
        return EspecialistaResponse.from(especialistaRepository.saveAndFlush(e));
    }

    @Transactional
    public EspecialistaResponse cambiarEstadoEspecialista(Long id, EstadoRequest req) {
        Especialista e = buscarEspecialista(id);
        e.setActivo(req.activo());
        return EspecialistaResponse.from(e);
    }

    private Servicio buscarServicio(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));
    }

    private void copiar(ServicioRequest req, Servicio s) {
        s.setNombre(req.nombre());
        s.setDescripcion(req.descripcion());
        s.setDuracionMinutos(req.duracionMinutos());
        s.setPrecio(req.precio());
    }

    private Especialista buscarEspecialista(Long id) {
        return especialistaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialista no encontrado"));
    }

    private void copiar(EspecialistaRequest req, Especialista e) {
        e.setNombre(req.nombre());
        e.setEmail(req.email());
        e.setEspecialidad(req.especialidad());
    }
}