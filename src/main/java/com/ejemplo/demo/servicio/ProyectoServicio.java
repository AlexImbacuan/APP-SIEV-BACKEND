package com.ejemplo.demo.servicio;

import com.ejemplo.demo.modelo.Proyecto;

import java.util.List;
import java.util.Optional;

public interface ProyectoServicio {

    List<Proyecto> listarTodos();

    Optional<Proyecto> buscarPorSerial(String serial);

    Proyecto guardar(Proyecto proyecto);

    Proyecto actualizar(String serial, Proyecto proyecto);

    void eliminar(String serial);

}