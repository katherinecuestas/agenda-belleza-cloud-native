package com.belleza.reservas.service;

import com.belleza.reservas.dto.CitaRequest;
import com.belleza.reservas.dto.CitaResponse;
import com.belleza.reservas.dto.ResolucionRequest;
import com.belleza.reservas.exception.ReglaNegocioException;
import com.belleza.reservas.mensajeria.CitaEvento;
import com.belleza.reservas.mensajeria.TipoEventoCita;
import com.belleza.reservas.model.Cita;
import com.belleza.reservas.model.Especialista;
import com.belleza.reservas.model.Servicio;
import com.belleza.reservas.model.enums.EstadoCita;
import com.belleza.reservas.repository.CitaRepository;
import com.belleza.reservas.repository.EspecialistaRepository;
import com.belleza.reservas.repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de CitaService: los repositorios y el publicador de eventos son mocks,
 * así que no necesitan base de datos ni RabbitMQ.
 */
@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    private static final String CLIENTA = "clienta-123";
    private static final Instant MANANA_10 = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

    @Mock
    private CitaRepository citaRepository;
    @Mock
    private ServicioRepository servicioRepository;
    @Mock
    private EspecialistaRepository especialistaRepository;
    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private CitaService citaService;

    private Servicio manicure;
    private Especialista camila;

    @BeforeEach
    void prepararCatalogo() {
        manicure = new Servicio();
        manicure.setId(1L);
        manicure.setNombre("Manicure");
        manicure.setDuracionMinutos(60);

        camila = new Especialista();
        camila.setId(2L);
        camila.setNombre("Camila");
    }

    /** El catálogo responde con un servicio y una especialista activos. */
    private void catalogoDisponible() {
        when(servicioRepository.findById(1L)).thenReturn(Optional.of(manicure));
        when(especialistaRepository.findById(2L)).thenReturn(Optional.of(camila));
    }

    /** Simula el save de JPA: devuelve la misma cita con un id asignado. */
    private void guardarAsignaId(long id) {
        when(citaRepository.save(any(Cita.class))).thenAnswer(invocacion -> {
            Cita cita = invocacion.getArgument(0);
            cita.setId(id);
            return cita;
        });
    }

    private Cita citaGuardada(long id, EstadoCita estado) {
        Cita cita = new Cita();
        cita.setId(id);
        cita.setServicio(manicure);
        cita.setEspecialista(camila);
        cita.setClienteId(CLIENTA);
        cita.setInicio(MANANA_10);
        cita.setFin(MANANA_10.plus(60, ChronoUnit.MINUTES));
        cita.setEstado(estado);
        return cita;
    }

    @Test
    void crearCita_conHorarioLibre_quedaPendienteConFinCalculado() {
        catalogoDisponible();
        when(citaRepository.existeSolapamiento(eq(2L), any(), any(), anyCollection(), isNull())).thenReturn(false);
        guardarAsignaId(10L);

        CitaResponse respuesta = citaService.crear(CLIENTA, new CitaRequest(1L, 2L, MANANA_10, "Uñas cortas"));

        assertEquals(10L, respuesta.id());
        assertEquals(EstadoCita.PENDIENTE, respuesta.estado());
        assertEquals("Manicure", respuesta.servicio());
        assertEquals("Camila", respuesta.especialista());
        assertEquals(CLIENTA, respuesta.clienteId());
        assertEquals(MANANA_10.plus(60, ChronoUnit.MINUTES), respuesta.fin());
    }

    @Test
    void crearCita_conHorarioOcupado_lanzaErrorYNoGuarda() {
        catalogoDisponible();
        when(citaRepository.existeSolapamiento(eq(2L), any(), any(), anyCollection(), isNull())).thenReturn(true);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> citaService.crear(CLIENTA, new CitaRequest(1L, 2L, MANANA_10, null)));

        assertTrue(error.getMessage().contains("ya tiene una cita en ese horario"));
        verify(citaRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void crearCita_conHorarioLibre_publicaEventoCitaCreada() {
        catalogoDisponible();
        when(citaRepository.existeSolapamiento(anyLong(), any(), any(), anyCollection(), isNull())).thenReturn(false);
        guardarAsignaId(10L);

        citaService.crear(CLIENTA, new CitaRequest(1L, 2L, MANANA_10, null));

        ArgumentCaptor<Object> eventoPublicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(eventoPublicado.capture());
        CitaEvento evento = assertInstanceOf(CitaEvento.class, eventoPublicado.getValue());
        assertEquals(TipoEventoCita.CREADA, evento.tipo());
        assertEquals(10L, evento.citaId());
        assertEquals(CLIENTA, evento.clienteId());
    }

    @Test
    void rechazarCita_sinComentario_lanzaErrorYNoCambiaEstado() {
        Cita pendiente = citaGuardada(20L, EstadoCita.PENDIENTE);
        when(citaRepository.findById(20L)).thenReturn(Optional.of(pendiente));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> citaService.resolver(20L, new ResolucionRequest(false, "  ")));

        assertTrue(error.getMessage().contains("comentario es obligatorio"));
        assertEquals(EstadoCita.PENDIENTE, pendiente.getEstado());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void cancelarCita_queNoEstaPendiente_lanzaError() {
        Cita aprobada = citaGuardada(30L, EstadoCita.APROBADA);
        when(citaRepository.findById(30L)).thenReturn(Optional.of(aprobada));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> citaService.cancelar(30L, CLIENTA));

        assertTrue(error.getMessage().contains("Solo se puede modificar una cita PENDIENTE"));
        assertEquals(EstadoCita.APROBADA, aprobada.getEstado());
        verify(eventos, never()).publishEvent(any(Object.class));
    }
}
