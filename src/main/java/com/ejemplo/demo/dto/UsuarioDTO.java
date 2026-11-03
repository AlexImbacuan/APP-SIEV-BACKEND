package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Usuario;

import java.util.List;

/** Usuario sin password, con su rol y sus permisos efectivos. */
public record UsuarioDTO(
        Long id,
        String nombre,
        String email,
        boolean activo,
        String rol,
        List<String> permisos
) {

    public static UsuarioDTO desde(Usuario usuario, List<String> permisos) {
        return new UsuarioDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.estaActivo(),
                usuario.getRol() != null ? usuario.getRol().getNombre() : null,
                permisos
        );
    }
}
