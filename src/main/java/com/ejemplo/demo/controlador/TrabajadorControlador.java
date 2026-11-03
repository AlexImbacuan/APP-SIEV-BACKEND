package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.ModuloDTO;
import com.ejemplo.demo.servicio.ModuloServicio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Módulo de Trabajador. Requiere el permiso trabajador:ver.
 * Aquí irán los endpoints reales del módulo; protege cada uno con
 * @PreAuthorize("hasAuthority('trabajador:accion')") y agrega el permiso a Permisos.CATALOGO.
 */
@RestController
@RequestMapping("/api/trabajador")
public class TrabajadorControlador {

    private final ModuloServicio moduloServicio;

    public TrabajadorControlador(ModuloServicio moduloServicio) {
        this.moduloServicio = moduloServicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('trabajador:ver')")
    public ModuloDTO ver() {
        return moduloServicio.contenido("trabajador", "Trabajador");
    }
}
