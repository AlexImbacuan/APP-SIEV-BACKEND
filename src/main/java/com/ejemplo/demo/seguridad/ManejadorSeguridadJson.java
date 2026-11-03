package com.ejemplo.demo.seguridad;

import com.ejemplo.demo.dto.ErrorRespuesta;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respuestas JSON de Spring Security:
 * - 401: no hay token, o es inválido/expirado, o el usuario está desactivado.
 * - 403: el usuario está autenticado pero no tiene el permiso requerido.
 */
@Component
public class ManejadorSeguridadJson implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String MENSAJE_401 =
            "Se requiere autenticación: el token falta, es inválido, expiró o el usuario está desactivado";
    public static final String MENSAJE_403 = "No tienes permiso para realizar esta acción";

    private final ObjectMapper objectMapper;

    public ManejadorSeguridadJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        escribir(response, HttpStatus.UNAUTHORIZED, MENSAJE_401, request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escribir(response, HttpStatus.FORBIDDEN, MENSAJE_403, request.getRequestURI());
    }

    private void escribir(HttpServletResponse response, HttpStatus estado,
                          String mensaje, String ruta) throws IOException {
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorRespuesta.de(estado, mensaje, ruta));
    }
}
