package com.ejemplo.demo.dto;

import java.util.List;

/** Lista completa de códigos de permiso: reemplaza los que tenía el rol. */
public record ActualizarPermisosRequest(List<String> permisos) {
}
