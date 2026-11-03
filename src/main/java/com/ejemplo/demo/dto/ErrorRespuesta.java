package com.ejemplo.demo.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/** Formato JSON único para todos los errores de la API. */
public record ErrorRespuesta(
        String fecha,
        int estado,
        String error,
        String mensaje,
        String ruta
) {

    public static ErrorRespuesta de(HttpStatus estado, String mensaje, String ruta) {
        return new ErrorRespuesta(
                LocalDateTime.now().toString(),
                estado.value(),
                estado.getReasonPhrase(),
                mensaje,
                ruta
        );
    }
}
