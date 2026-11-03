package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.ModuloDTO;
import com.ejemplo.demo.servicio.ModuloServicio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Módulo de Administración. Requiere el permiso administracion:ver.
 * Aquí irán los endpoints reales del módulo; protege cada uno con
 * @PreAuthorize("hasAuthority('administracion:accion')") y agrega el permiso a Permisos.CATALOGO.
 */
@RestController
@RequestMapping("/api/administracion")
public class AdministracionControlador {

    private final ModuloServicio moduloServicio;

    public AdministracionControlador(ModuloServicio moduloServicio) {
        this.moduloServicio = moduloServicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('administracion:ver')")
    public ModuloDTO ver() {
        return moduloServicio.contenido("administracion", "Administración");
    }
}
