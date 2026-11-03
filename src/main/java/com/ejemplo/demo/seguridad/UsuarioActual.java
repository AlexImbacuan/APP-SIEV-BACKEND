package com.ejemplo.demo.seguridad;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al email del usuario autenticado en la petición actual. */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    /** Email del usuario autenticado, o null si no hay ninguno. */
    public static String email() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return auth.getName();
    }
}
