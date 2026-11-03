package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Permiso;

public record PermisoDTO(Long id, String codigo, String descripcion, String modulo) {

    public static PermisoDTO desde(Permiso permiso) {
        return new PermisoDTO(permiso.getId(), permiso.getCodigo(), permiso.getDescripcion(), permiso.getModulo());
    }
}
