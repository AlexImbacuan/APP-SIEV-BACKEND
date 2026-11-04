package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Usuario;

/** Usuario que puede ser responsable SIEV de una cotización. */
public record ResponsableDTO(Long id, String nombre) {

    public static ResponsableDTO desde(Usuario usuario) {
        return usuario == null ? null : new ResponsableDTO(usuario.getId(), usuario.getNombre());
    }
}
