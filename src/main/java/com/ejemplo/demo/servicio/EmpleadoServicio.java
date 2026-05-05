package com.ejemplo.demo.servicio;

import com.ejemplo.demo.modelo.Empleado;

import java.util.List;

public interface EmpleadoServicio {
    Empleado guardarEmpleado(Empleado empleado);
    Empleado obtenerEmpleadoPorId(Long idEmpleado);
    List<Empleado> obtenerTodosLosEmpleados();
    Empleado actualizarEmpleado(Long idEmpleado, Empleado empleado);
    void eliminarEmpleado(Long idEmpleado);
    List<Empleado> obtenerEmpleadosPorDepartamento(String departamento);
    List<Empleado> obtenerEmpleadosConSueldoMayorA(Double sueldo);
}