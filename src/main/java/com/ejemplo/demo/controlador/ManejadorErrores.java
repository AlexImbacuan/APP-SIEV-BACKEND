package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.ErrorRespuesta;
import com.ejemplo.demo.seguridad.ManejadorSeguridadJson;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Convierte las excepciones lanzadas por los controladores en JSON
 * con el mismo formato que los 401/403 de Spring Security.
 */
@RestControllerAdvice
public class ManejadorErrores {

    /** 403 lanzado por @PreAuthorize. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorRespuesta> accesoDenegado(HttpServletRequest request) {
        return respuesta(HttpStatus.FORBIDDEN, ManejadorSeguridadJson.MENSAJE_403, request);
    }

    /** 401, por ejemplo un login con credenciales incorrectas o un usuario desactivado. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorRespuesta> noAutenticado(AuthenticationException ex, HttpServletRequest request) {
        return respuesta(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /** Errores de negocio: 400, 403, 404, 409... */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorRespuesta> errorConEstado(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus estado = HttpStatus.valueOf(ex.getStatusCode().value());
        return respuesta(estado, ex.getReason(), request);
    }

    /** 400 cuando un parámetro no tiene el tipo esperado (ej. desde=ayer en lugar de 2026-11-01). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> parametroInvalido(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "Valor inválido para el parámetro '" + ex.getName() + "': " + ex.getValue(), request);
    }

    private ResponseEntity<ErrorRespuesta> respuesta(HttpStatus estado, String mensaje, HttpServletRequest request) {
        return ResponseEntity.status(estado)
                .body(ErrorRespuesta.de(estado, mensaje, request.getRequestURI()));
    }
}
