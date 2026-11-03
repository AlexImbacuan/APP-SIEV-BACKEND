package com.ejemplo.demo.seguridad;

import com.ejemplo.demo.modelo.Permiso;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.PermisoRepositorio;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula los permisos efectivos de un usuario a partir de la BD.
 * - Sin rol: ningún permiso.
 * - SUPERADMIN: todos los permisos que existan (incluidos los que se agreguen después).
 * - Otro rol: los permisos asignados a ese rol.
 */
@Component
public class CargadorPermisos {

    private final PermisoRepositorio permisoRepositorio;

    public CargadorPermisos(PermisoRepositorio permisoRepositorio) {
        this.permisoRepositorio = permisoRepositorio;
    }

    public List<String> codigosDe(Usuario usuario) {

        if (usuario.getRol() == null) {
            return List.of();
        }

        var permisos = usuario.tieneRol(Permisos.ROL_SUPERADMIN)
                ? permisoRepositorio.findAll()
                : usuario.getRol().getPermisos();

        return permisos.stream()
                .map(Permiso::getCodigo)
                .sorted()
                .toList();
    }

    public List<GrantedAuthority> authoritiesDe(Usuario usuario) {

        List<GrantedAuthority> authorities = new ArrayList<>();

        codigosDe(usuario).forEach(codigo -> authorities.add(new SimpleGrantedAuthority(codigo)));

        if (usuario.getRol() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getNombre()));
        }

        return authorities;
    }
}
