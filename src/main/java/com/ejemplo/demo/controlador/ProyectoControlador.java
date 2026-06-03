package com.ejemplo.demo.controlador;

import com.ejemplo.demo.modelo.Proyecto;
import com.ejemplo.demo.servicio.ProyectoServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoControlador {

    private final ProyectoServicio proyectoServicio;

    @Autowired
    public ProyectoControlador(ProyectoServicio proyectoServicio) {
        this.proyectoServicio = proyectoServicio;
    }

    @PostMapping
    public ResponseEntity<Proyecto> guardarProyecto(@RequestBody Proyecto proyecto) {
        Proyecto nuevoProyecto = proyectoServicio.guardarProyecto(proyecto);
        return new ResponseEntity<>(nuevoProyecto, HttpStatus.CREATED);
    }

    @GetMapping("/{serial}")
    public ResponseEntity<Proyecto> obtenerProyectoPorSerial(@PathVariable String serial) {
        Proyecto proyecto = proyectoServicio.obtenerProyectoPorSerial(serial);
        return proyecto != null ? ResponseEntity.ok(proyecto) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<Proyecto>> obtenerTodosLosProyectos() {
        List<Proyecto> proyectos = proyectoServicio.obtenerTodosLosProyectos();
        return ResponseEntity.ok(proyectos);
    }

    @PutMapping("/{serial}")
    public ResponseEntity<Proyecto> actualizarProyecto(@PathVariable String serial, @RequestBody Proyecto proyecto) {
        Proyecto proyectoActualizado = proyectoServicio.actualizarProyecto(serial, proyecto);
        return proyectoActualizado != null ? ResponseEntity.ok(proyectoActualizado) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{serial}")
    public ResponseEntity<Void> eliminarProyecto(@PathVariable String serial) {
        proyectoServicio.eliminarProyecto(serial);
        return ResponseEntity.noContent().build();
    }
}