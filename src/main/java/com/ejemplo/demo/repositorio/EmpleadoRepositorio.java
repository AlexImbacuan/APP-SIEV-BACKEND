package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmpleadoRepositorio extends JpaRepository<Empleado, Long> {

    // Método para buscar empleados por departamento
    List<Empleado> findByDepartamento(String departamento);

    // Método para buscar empleados con sueldo mayor a un valor dado
    List<Empleado> findBySueldoGreaterThan(Double sueldo);
}