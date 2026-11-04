package com.ejemplo.demo.configuracion;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API con springdoc-openapi.
 *   - Swagger UI:        /swagger-ui.html
 *   - Especificación:    /v3/api-docs
 * Define el esquema "bearerAuth" para que el botón "Authorize" de Swagger UI
 * envíe el JWT en la cabecera Authorization de cada petición.
 */
@Configuration
public class OpenApiConfiguracion {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("APP-SIEV API")
                        .version("v1")
                        .description("API REST del sistema SIEV. Inicia sesión en POST /api/auth/login, "
                                + "copia el accessToken y pégalo en el botón \"Authorize\"."))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
