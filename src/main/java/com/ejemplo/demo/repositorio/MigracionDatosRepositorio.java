package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.MigracionDatos;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigracionDatosRepositorio extends JpaRepository<MigracionDatos, String> {
}
