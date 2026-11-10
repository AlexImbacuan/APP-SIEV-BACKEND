package com.ejemplo.demo.dto;

/** { "nit": "800197463", "nombre": "Cargill de Colombia", "activo": true } (activo es opcional) */
public record ClienteRequest(String nit, String nombre, Boolean activo) {
}
