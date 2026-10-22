package com.belleza.reservas.service;

import com.belleza.reservas.dto.CitaRequest;
import com.belleza.reservas.dto.CitaResponse;
import com.belleza.reservas.dto.ResolucionRequest;
import com.belleza.reservas.exception.RecursoNoEncontradoException;
import com.belleza.reservas.exception.ReglaNegocioException;
import com.belleza.reservas.mensajeria.CitaEvento;
import com.belleza.reservas.mensajeria.TipoEventoCita;
import com.belleza.reservas.model.Cita;
import com.belleza.reservas.model.Especialista;
import com.belleza.reservas.model.enums.EstadoCita;
import com.belleza.reservas.model.Servicio;
import com.belleza.reservas.repository.CitaRepository;
import com.belleza.reservas.repository.EspecialistaRepository;
import com.belleza.reservas.repository.ServicioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CitaService {

    private static final List<EstadoCita> ESTADOS_ACTIVOS = List.of(EstadoCita.PENDIENTE, EstadoCita.APROBADA);

    private final CitaRepository citaRepository;
    private final ServicioRepository servicioRepository;
    private final EspecialistaRepository especialistaRepository;
    // Solo eventos de Spring: el service no sabe que existe RabbitMQ (ver mensajeria/CitaEventoPublicador)
    private final ApplicationEventPublisher eventos;

    public CitaService(CitaRepository citaRepository,
            ServicioRepository servicioRepository,
            EspecialistaRepository especialistaRepository,
            ApplicationEventPublisher eventos) {
        this.citaRepository = citaRepository;
        this.servicioRepository = servicioRepository;
        this.especialistaRepository = especialistaRepository;
        this.eventos = eventos;
    }

    // ───────────── SOLICITANTE (cliente) ─────────────

    @Transactional
    public CitaResponse crear(String clienteId, CitaRequest req) {
        Servicio servicio = buscarServicioActivo(req.servicioId());
        Especialista especialista = buscarEspecialistaActivo(req.especialistaId());
        Instant fin = calcularFin(req.inicio(), servicio);

        validarDisponibilidad(especialista.getId(), req.inicio(), fin, null);

        Cita cita = new Cita();
        cita.setServicio(servicio);
        cita.setEspecialista(especialista);
        cita.setClienteId(clienteId);
        cita.setInicio(req.inicio());
        cita.setFin(fin);
        cita.setNotasCliente(req.notasCliente());

        Cita guardada = citaRepository.save(cita);
        eventos.publishEvent(CitaEvento.de(TipoEventoCita.CREADA, guardada));
        return CitaResponse.from(guardada);
    }

    @Transactional(readOnly = true)
    public List<CitaResponse> listarMias(String clienteId) {
        return citaRepository.findByClienteIdOrderByInicioDesc(clienteId)
                .stream()
                .map(CitaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CitaResponse obtenerMia(Long id, String clienteId) {
        return CitaResponse.from(buscarCitaDelCliente(id, clienteId));
    }

    @Transactional
    public CitaResponse actualizar(Long id, String clienteId, CitaRequest req) {
        Cita cita = buscarCitaDelCliente(id, clienteId);
        exigirPendiente(cita);

        Servicio servicio = buscarServicioActivo(req.servicioId());
        Especialista especialista = buscarEspecialistaActivo(req.especialistaId());
        Instant fin = calcularFin(req.inicio(), servicio);

        validarDisponibilidad(especialista.getId(), req.inicio(), fin, cita.getId());

        cita.setServicio(servicio);
        cita.setEspecialista(especialista);
        cita.setInicio(req.inicio());
        cita.setFin(fin);
        cita.setNotasCliente(req.notasCliente());

        return CitaResponse.from(cita);
    }

    @Transactional
    public CitaResponse cancelar(Long id, String clienteId) {
        Cita cita = buscarCitaDelCliente(id, clienteId);
        exigirPendiente(cita);
        cita.setEstado(EstadoCita.CANCELADA);
        eventos.publishEvent(CitaEvento.de(TipoEventoCita.CANCELADA, cita));
        return CitaResponse.from(cita);
    }

    // ───────────── APROBADOR ─────────────

    @Transactional(readOnly = true)
    public List<CitaResponse> listarPendientes() {
        return citaRepository.findByEstadoOrderByInicioAsc(EstadoCita.PENDIENTE)
                .stream()
                .map(CitaResponse::from)
                .toList();
    }

    @Transactional
    public CitaResponse resolver(Long id, ResolucionRequest req) {
        Cita cita = citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));
        exigirPendiente(cita);

        boolean sinComentario = req.comentario() == null || req.comentario().isBlank();
        if (!req.aprobar() && sinComentario) {
            throw new ReglaNegocioException("El comentario es obligatorio al rechazar");
        }

        cita.setEstado(req.aprobar() ? EstadoCita.APROBADA : EstadoCita.RECHAZADA);
        cita.setComentarioAprobador(req.comentario());
        eventos.publishEvent(CitaEvento.de(
                req.aprobar() ? TipoEventoCita.APROBADA : TipoEventoCita.RECHAZADA, cita));
        return CitaResponse.from(cita);
    }

    // ───────────── AUXILIARES ─────────────

    private Servicio buscarServicioActivo(Long id) {
        Servicio s = servicioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));
        if (!s.getActivo()) {
            throw new ReglaNegocioException("El servicio no está disponible");
        }
        return s;
    }

    private Especialista buscarEspecialistaActivo(Long id) {
        Especialista e = especialistaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialista no encontrado"));
        if (!e.getActivo()) {
            throw new ReglaNegocioException("El especialista no está disponible");
        }
        return e;
    }

    private Cita buscarCitaDelCliente(Long id, String clienteId) {
        return citaRepository.findById(id)
                .filter(c -> c.getClienteId().equals(clienteId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));
    }

    private void exigirPendiente(Cita cita) {
        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            throw new ReglaNegocioException(
                    "Solo se puede modificar una cita PENDIENTE. Estado actual: " + cita.getEstado());
        }
    }

    private Instant calcularFin(Instant inicio, Servicio servicio) {
        return inicio.plus(servicio.getDuracionMinutos(), ChronoUnit.MINUTES);
    }

    private void validarDisponibilidad(Long especialistaId, Instant inicio, Instant fin, Long idExcluir) {
        if (citaRepository.existeSolapamiento(especialistaId, inicio, fin, ESTADOS_ACTIVOS, idExcluir)) {
            throw new ReglaNegocioException("El especialista ya tiene una cita en ese horario");
        }
    }
}