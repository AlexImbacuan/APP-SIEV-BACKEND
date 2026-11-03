package com.ejemplo.demo.servicio;

import com.ejemplo.demo.modelo.Proyecto;
import com.ejemplo.demo.repositorio.ProyectoRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class ProyectoServicioImpl implements ProyectoServicio {

    private final ProyectoRepositorio proyectoRepositorio;

    public ProyectoServicioImpl(ProyectoRepositorio proyectoRepositorio) {
        this.proyectoRepositorio = proyectoRepositorio;
    }

    @Override
    public List<Proyecto> listarTodos() {
        return proyectoRepositorio.findAll();
    }

    @Override
    public Optional<Proyecto> buscarPorSerial(String serial) {
        return proyectoRepositorio.findById(serial);
    }

    @Override
    public Proyecto guardar(Proyecto proyecto) {
        return proyectoRepositorio.save(proyecto);
    }

    @Override
    public Proyecto actualizar(String serial, Proyecto proyecto) {

        Proyecto proyectoActual = proyectoRepositorio.findById(serial)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));

        proyectoActual.setDescripcionGlobal(proyecto.getDescripcionGlobal());
        proyectoActual.setValorTotal(proyecto.getValorTotal());
        proyectoActual.setFactura(proyecto.getFactura());
        proyectoActual.setPago(proyecto.getPago());
        proyectoActual.setObservacion(proyecto.getObservacion());

        return proyectoRepositorio.save(proyectoActual);
    }

    @Override
    public void eliminar(String serial) {

        if (!proyectoRepositorio.existsById(serial)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
        }

        proyectoRepositorio.deleteById(serial);
    }
}
