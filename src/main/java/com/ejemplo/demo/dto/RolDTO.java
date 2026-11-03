package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Permiso;
import com.ejemplo.demo.modelo.Rol;

import java.util.List;

public record RolDTO(Long id, String nombre, String descripcion, List<String> permisos) {

    public static RolDTO desde(Rol rol) {
        List<String> codigos = rol.getPermisos().stream()
                .map(Permiso::getCodigo)
                .sorted()
                .toList();
        return new RolDTO(rol.getId(), rol.getNombre(), rol.getDescripcion(), codigos);
    }
}
