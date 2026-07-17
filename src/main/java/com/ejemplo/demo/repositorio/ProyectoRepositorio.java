package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProyectoRepositorio extends JpaRepository<Proyecto, String> {

}