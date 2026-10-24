package com.belleza.rabbitadmin.controller;

import com.belleza.rabbitadmin.dto.BindingRequest;
import com.belleza.rabbitadmin.dto.ColaInfoResponse;
import com.belleza.rabbitadmin.dto.CrearColaRequest;
import com.belleza.rabbitadmin.dto.CrearExchangeRequest;
import com.belleza.rabbitadmin.dto.ExchangeResponse;
import com.belleza.rabbitadmin.dto.TipoExchange;
import com.belleza.rabbitadmin.exception.ConflictoException;
import com.belleza.rabbitadmin.exception.RecursoNoEncontradoException;
import com.belleza.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos exitosos y de error de AdminController con RabbitAdminService mockeado:
 * pasa por la seguridad real (JWT con rol APROBADORES), @Valid y ManejadorErrores,
 * pero no toca RabbitMQ. spring.rabbitmq.addresses=localhost:1: aunque algo intentara
 * conectarse, ahí no hay broker.
 */
@SpringBootTest(properties = "spring.rabbitmq.addresses=localhost:1")
@AutoConfigureMockMvc
class AdminControllerTest {

    private static final String BINDING_JSON =
            "{\"exchange\": \"demo.topic\", \"cola\": \"demo.cola\", \"routingKey\": \"demo.#\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RabbitAdminService servicio;

    private static JwtRequestPostProcessor aprobadora() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_APROBADORES"));
    }

    // ───────────── Colas ─────────────

    @Test
    void crearCola_conDatosValidos_responde201ConLaCola() throws Exception {
        when(servicio.crearCola(any(CrearColaRequest.class))).thenReturn(new ColaInfoResponse("demo.cola", 0, 0));

        mvc.perform(post("/v1/admin/queues").with(aprobadora())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"demo.cola\", \"tipo\": \"QUORUM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("demo.cola"))
                .andExpect(jsonPath("$.mensajes").value(0));
    }

    @Test
    void eliminarCola_vacia_responde204() throws Exception {
        mvc.perform(delete("/v1/admin/queues/demo.cola").with(aprobadora()))
                .andExpect(status().isNoContent());

        // soloSiVacia vale true por defecto
        verify(servicio).eliminarCola("demo.cola", true);
    }

    @Test
    void eliminarCola_protegidaDelSalon_responde409() throws Exception {
        doThrow(new ConflictoException("'salon.notificaciones' es un recurso protegido del sistema de reservas"))
                .when(servicio).eliminarCola("salon.notificaciones", true);

        mvc.perform(delete("/v1/admin/queues/salon.notificaciones").with(aprobadora()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.detail", containsString("recurso protegido")));
    }

    @Test
    void eliminarCola_queNoExiste_responde404() throws Exception {
        doThrow(new RecursoNoEncontradoException("La cola 'no.existe' no existe"))
                .when(servicio).eliminarCola("no.existe", true);

        mvc.perform(delete("/v1/admin/queues/no.existe").with(aprobadora()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No existe"))
                .andExpect(jsonPath("$.detail").value("La cola 'no.existe' no existe"));
    }

    // ───────────── Exchanges ─────────────

    @Test
    void crearExchange_conDatosValidos_responde201ConElExchange() throws Exception {
        when(servicio.crearExchange(any(CrearExchangeRequest.class)))
                .thenReturn(new ExchangeResponse("demo.topic", TipoExchange.TOPIC, true));

        mvc.perform(post("/v1/admin/exchanges").with(aprobadora())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"demo.topic\", \"tipo\": \"TOPIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("demo.topic"))
                .andExpect(jsonPath("$.tipo").value("TOPIC"))
                .andExpect(jsonPath("$.durable").value(true));
    }

    @Test
    void eliminarExchange_existente_responde204() throws Exception {
        mvc.perform(delete("/v1/admin/exchanges/demo.topic").with(aprobadora()))
                .andExpect(status().isNoContent());

        verify(servicio).eliminarExchange("demo.topic");
    }

    // ───────────── Bindings ─────────────

    @Test
    void crearBinding_conDatosValidos_responde201ConElBinding() throws Exception {
        when(servicio.crearBinding(any(BindingRequest.class)))
                .thenReturn(new BindingRequest("demo.topic", "demo.cola", "demo.#"));

        mvc.perform(post("/v1/admin/bindings").with(aprobadora())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BINDING_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchange").value("demo.topic"))
                .andExpect(jsonPath("$.cola").value("demo.cola"))
                .andExpect(jsonPath("$.routingKey").value("demo.#"));
    }

    @Test
    void eliminarBinding_existente_responde204() throws Exception {
        mvc.perform(delete("/v1/admin/bindings").with(aprobadora())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BINDING_JSON))
                .andExpect(status().isNoContent());

        verify(servicio).eliminarBinding(new BindingRequest("demo.topic", "demo.cola", "demo.#"));
    }
}
