package com.ejemplo.demo.servicio;

import com.ejemplo.demo.modelo.Empleado;
import com.ejemplo.demo.repositorio.EmpleadoRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmpleadoServicioImpl implements EmpleadoServicio {

    private final EmpleadoRepositorio empleadoRepositorio;

    @Autowired
    public EmpleadoServicioImpl(EmpleadoRepositorio empleadoRepositorio) {
        this.empleadoRepositorio = empleadoRepositorio;
    }

    @Override
    public Empleado guardarEmpleado(Empleado empleado) {
        return empleadoRepositorio.save(empleado);
    }

    @Override
    public Empleado obtenerEmpleadoPorId(Long idEmpleado) {
        Optional<Empleado> empleado = empleadoRepositorio.findById(idEmpleado);
        return empleado.orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + idEmpleado));
    }

    @Override
    public List<Empleado> obtenerTodosLosEmpleados() {
        return empleadoRepositorio.findAll();
    }

    @Override
    public Empleado actualizarEmpleado(Long idEmpleado, Empleado empleado) {
        Empleado empleadoExistente = obtenerEmpleadoPorId(idEmpleado);
        empleadoExistente.setNombre(empleado.getNombre());
        empleadoExistente.setDepartamento(empleado.getDepartamento());
        empleadoExistente.setSueldo(empleado.getSueldo());
        return empleadoRepositorio.save(empleadoExistente);
    }

    @Override
    public void eliminarEmpleado(Long idEmpleado) {
        empleadoRepositorio.deleteById(idEmpleado);
    }

    @Override
    public List<Empleado> obtenerEmpleadosPorDepartamento(String departamento) {
        return empleadoRepositorio.findByDepartamento(departamento);
    }

    @Override
    public List<Empleado> obtenerEmpleadosConSueldoMayorA(Double sueldo) {
        return empleadoRepositorio.findBySueldoGreaterThan(sueldo);
    }
}