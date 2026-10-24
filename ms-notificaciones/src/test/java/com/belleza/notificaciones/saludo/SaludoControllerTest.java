package com.belleza.notificaciones.saludo;

import com.belleza.notificaciones.exception.ManejadorErrores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de SaludoController con MockMvc standalone: solo el controller, el ManejadorErrores
 * y el validador. El productor es un mock, así que no necesita RabbitMQ.
 */
class SaludoControllerTest {

    private SaludoProductor productor;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        productor = mock(SaludoProductor.class);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new SaludoController(productor))
                .setControllerAdvice(new ManejadorErrores())
                .setValidator(validador)
                .build();
    }

    @Test
    void enviarSaludo_conMensajeVacio_responde400ConProblemDetail() throws Exception {
        mvc.perform(post("/v1/saludos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("mensaje")));

        verify(productor, never()).enviar(anyString());
    }

    @Test
    void enviarSaludo_conMensaje_responde202YLoEnvia() throws Exception {
        mvc.perform(post("/v1/saludos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\": \"Hola salon\"}"))
                .andExpect(status().isAccepted());

        verify(productor).enviar("Hola salon");
    }
}
