package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.ErrorRespuesta;
import com.ejemplo.demo.seguridad.ManejadorSeguridadJson;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    /** 413 cuando el archivo subido supera spring.servlet.multipart.max-file-size. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorRespuesta> archivoGrande(HttpServletRequest request) {
        return respuesta(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo es demasiado grande (máximo 10 MB)", request);
    }

    /** 400 cuando falta el archivo, o la petición no es multipart/form-data. */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
            MultipartException.class})
    public ResponseEntity<ErrorRespuesta> faltaParametro(Exception ex, HttpServletRequest request) {
        String mensaje = ex instanceof MissingServletRequestPartException e
                ? "Falta el campo '" + e.getRequestPartName() + "'"
                : ex instanceof MissingServletRequestParameterException e
                ? "Falta el parámetro '" + e.getParameterName() + "'"
                : "La petición debe enviarse como multipart/form-data";
        return respuesta(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    /** 400 cuando el JSON está mal formado o trae un valor inválido (ej. un estado que no existe). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonInvalido(HttpServletRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es válido: revisa el formato del JSON y los valores enviados", request);
    }

    private ResponseEntity<ErrorRespuesta> respuesta(HttpStatus estado, String mensaje, HttpServletRequest request) {
        return ResponseEntity.status(estado)
                .body(ErrorRespuesta.de(estado, mensaje, request.getRequestURI()));
    }
}
