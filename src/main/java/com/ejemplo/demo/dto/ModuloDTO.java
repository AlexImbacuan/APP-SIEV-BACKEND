package com.ejemplo.demo.dto;

/**
 * Respuesta provisional de los módulos del frontend.
 * Cuando cada módulo tenga datos reales, se reemplaza por sus propios DTOs.
 */
public record ModuloDTO(
        String modulo,
        String titulo,
        String mensaje,
        String usuario,
        String rol
) {
}
