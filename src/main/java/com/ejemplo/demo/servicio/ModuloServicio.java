package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.ModuloDTO;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

/** Contenido provisional de los módulos (administración, ventas, trabajador). */
@Service
public class ModuloServicio {

    private final UsuarioRepositorio usuarioRepositorio;

    public ModuloServicio(UsuarioRepositorio usuarioRepositorio) {
        this.usuarioRepositorio = usuarioRepositorio;
    }

    public ModuloDTO contenido(String modulo, String titulo) {

        String email = UsuarioActual.email();
        String rol = usuarioRepositorio.findByEmail(email)
                .map(u -> u.getRol() != null ? u.getRol().getNombre() : null)
                .orElse(null);

        return new ModuloDTO(
                modulo,
                titulo,
                "Módulo de " + titulo + ": contenido pendiente",
                email,
                rol
        );
    }
}
