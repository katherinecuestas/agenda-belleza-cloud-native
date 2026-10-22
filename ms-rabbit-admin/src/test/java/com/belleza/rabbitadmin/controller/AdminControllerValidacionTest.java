package com.belleza.rabbitadmin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación de entrada y seguridad de la API, sin RabbitMQ ni Cognito:
 *  - jwt() simula un token ya validado (no llama a Cognito).
 *  - spring.rabbitmq.port=1: no hay broker ahí; sirve para probar el 503. Las validaciones
 *    fallan ANTES de llegar al servicio, así que nunca tocan el broker.
 */
@SpringBootTest(properties = "spring.rabbitmq.port=1")
@AutoConfigureMockMvc
class AdminControllerValidacionTest {

    @Autowired
    private MockMvc mvc;

    private static JwtRequestPostProcessor aprobadora() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_APROBADORES"));
    }

    private org.springframework.test.web.servlet.ResultActions crearCola(String json) throws Exception {
        return mvc.perform(post("/v1/admin/queues").with(aprobadora())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ───────────── Validación de CrearColaRequest ─────────────

    @Test
    void nombreVacioDa400() throws Exception {
        crearCola("{\"nombre\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItem("es obligatorio")));
    }

    @Test
    void nombreConPrefijoAmqDa400() throws Exception {
        crearCola("{\"nombre\": \"amq.x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].mensaje", hasItem(containsString("amq."))));
    }

    @Test
    void quorumNoDurableDa400() throws Exception {
        crearCola("{\"nombre\": \"demo.cola\", \"tipo\": \"QUORUM\", \"durable\": false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].mensaje", hasItem(containsString("QUORUM siempre es durable"))));
    }

    @Test
    void ttlNegativoDa400() throws Exception {
        crearCola("{\"nombre\": \"demo.cola\", \"ttlMs\": -5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("ttlMs")));
    }

    @Test
    void tipoDesconocidoDa400ConLosValoresPermitidos() throws Exception {
        crearCola("{\"nombre\": \"demo.cola\", \"tipo\": \"XYZ\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("tipo"))
                .andExpect(jsonPath("$.errores[0].mensaje", containsString("CLASSIC")));
    }

    @Test
    void nombreInvalidoEnLaUrlDa400() throws Exception {
        mvc.perform(get("/v1/admin/queues/ab").with(aprobadora()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")));
    }

    // ───────────── Seguridad ─────────────

    @Test
    void sinTokenDa401() throws Exception {
        mvc.perform(get("/v1/admin/queues/demo.cola"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientaSinRolAprobadoresDa403() throws Exception {
        mvc.perform(get("/v1/admin/queues/demo.cola")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTES"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ───────────── Broker caído ─────────────

    @Test
    void sinBrokerDa503() throws Exception {
        mvc.perform(get("/v1/admin/queues/demo.cola").with(aprobadora()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("RabbitMQ no disponible"));
    }
}
