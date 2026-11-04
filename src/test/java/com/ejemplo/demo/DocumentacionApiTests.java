package com.ejemplo.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La documentación OpenAPI y Swagger UI son públicas (no requieren token). */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentacionApiTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void especificacionOpenApiEsPublicaEIncluyeLosEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("APP-SIEV API"))
                .andExpect(jsonPath("$.paths", hasKey("/api/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/api/proyectos")))
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("bearerAuth")));
    }

    @Test
    void swaggerUiEsPublico() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
