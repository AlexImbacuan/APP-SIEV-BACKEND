package com.ejemplo.demo.dto;

import java.util.List;

public record CrearRolRequest(String nombre, String descripcion, List<String> permisos) {
}
