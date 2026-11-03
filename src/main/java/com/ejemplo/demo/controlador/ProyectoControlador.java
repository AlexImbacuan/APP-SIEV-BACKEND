package com.ejemplo.demo.controlador;

import com.ejemplo.demo.modelo.Proyecto;
import com.ejemplo.demo.servicio.ProyectoServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoControlador {

    private final ProyectoServicio proyectoServicio;

    public ProyectoControlador(ProyectoServicio proyectoServicio) {
        this.proyectoServicio = proyectoServicio;
    }

    @PreAuthorize("hasAuthority('proyectos:ver')")
    @GetMapping
    public List<Proyecto> listar() {
        return proyectoServicio.listarTodos();
    }

    @PreAuthorize("hasAuthority('proyectos:ver')")
    @GetMapping("/{serial}")
    public ResponseEntity<Proyecto> buscar(@PathVariable String serial) {

        return proyectoServicio.buscarPorSerial(serial)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('proyectos:crear')")
    @PostMapping
    public Proyecto guardar(@RequestBody Proyecto proyecto) {
        return proyectoServicio.guardar(proyecto);
    }

    @PreAuthorize("hasAuthority('proyectos:editar')")
    @PutMapping("/{serial}")
    public Proyecto actualizar(@PathVariable String serial,
                               @RequestBody Proyecto proyecto) {

        return proyectoServicio.actualizar(serial, proyecto);
    }

    @PreAuthorize("hasAuthority('proyectos:eliminar')")
    @DeleteMapping("/{serial}")
    public ResponseEntity<Void> eliminar(@PathVariable String serial) {

        proyectoServicio.eliminar(serial);

        return ResponseEntity.noContent().build();
    }

}