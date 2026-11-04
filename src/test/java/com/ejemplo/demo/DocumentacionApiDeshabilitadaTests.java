package com.ejemplo.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con Swagger apagado (lo que pasa en producción) la documentación no está disponible. */
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
class DocumentacionApiDeshabilitadaTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void especificacionOpenApiNoDisponible() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiNoDisponible() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}
