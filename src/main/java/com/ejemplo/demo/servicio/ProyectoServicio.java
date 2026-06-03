package com.ejemplo.demo.servicio;

import com.ejemplo.demo.modelo.Proyecto;
import com.ejemplo.demo.repositorio.ProyectoRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProyectoServicio {

    private final ProyectoRepositorio proyectoRepositorio;

    @Autowired
    public ProyectoServicio(ProyectoRepositorio proyectoRepositorio) {
        this.proyectoRepositorio = proyectoRepositorio;
    }

    public Proyecto guardarProyecto(Proyecto proyecto) {
        return proyectoRepositorio.save(proyecto);
    }

    public Proyecto obtenerProyectoPorSerial(String serial) {
        return proyectoRepositorio.findById(serial).orElse(null);
    }

    public List<Proyecto> obtenerTodosLosProyectos() {
        return proyectoRepositorio.findAll();
    }

    public Proyecto actualizarProyecto(String serial, Proyecto proyecto) {
        if (proyectoRepositorio.existsById(serial)) {
            proyecto.setSerial(serial);
            return proyectoRepositorio.save(proyecto);
        }
        return null;
    }

    public void eliminarProyecto(String serial) {
        proyectoRepositorio.deleteById(serial);
    }
}