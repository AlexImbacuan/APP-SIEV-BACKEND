package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.ModuloDTO;
import com.ejemplo.demo.servicio.ModuloServicio;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Módulo de Ventas. Requiere el permiso ventas:ver.
 * Aquí irán los endpoints reales del módulo; protege cada uno con
 * @PreAuthorize("hasAuthority('ventas:accion')") y agrega el permiso a Permisos.CATALOGO.
 */
@RestController
@RequestMapping("/api/ventas")
public class VentasControlador {

    private final ModuloServicio moduloServicio;

    public VentasControlador(ModuloServicio moduloServicio) {
        this.moduloServicio = moduloServicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ventas:ver')")
    public ModuloDTO ver() {
        return moduloServicio.contenido("ventas", "Ventas");
    }
}
