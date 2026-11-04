package com.ejemplo.demo.controlador;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.ejemplo.demo.modelo.Empleado;
import com.ejemplo.demo.servicio.EmpleadoServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Empleados", description = "CRUD de empleados. Requiere empleados:ver / crear / editar / eliminar.")
@RestController
@RequestMapping("/api/empleados")
public class EmpleadoControlador {

    private final EmpleadoServicio empleadoServicio;

    @Autowired
    public EmpleadoControlador(EmpleadoServicio empleadoServicio) {
        this.empleadoServicio = empleadoServicio;
    }

    @PreAuthorize("hasAuthority('empleados:crear')")
    @PostMapping
    public ResponseEntity<Empleado> guardarEmpleado(@RequestBody Empleado empleado) {
        Empleado nuevoEmpleado = empleadoServicio.guardarEmpleado(empleado);
        return new ResponseEntity<>(nuevoEmpleado, HttpStatus.CREATED);
    }

    @PreAuthorize("hasAuthority('empleados:ver')")
    @GetMapping("/{id}")
    public ResponseEntity<Empleado> obtenerEmpleadoPorId(@PathVariable Long id) {
        Empleado empleado = empleadoServicio.obtenerEmpleadoPorId(id);
        return ResponseEntity.ok(empleado);
    }

    @PreAuthorize("hasAuthority('empleados:ver')")
    @GetMapping
    public ResponseEntity<List<Empleado>> obtenerTodosLosEmpleados() {
        List<Empleado> empleados = empleadoServicio.obtenerTodosLosEmpleados();
        return ResponseEntity.ok(empleados);
    }

    @PreAuthorize("hasAuthority('empleados:editar')")
    @PutMapping("/{id}")
    public ResponseEntity<Empleado> actualizarEmpleado(@PathVariable Long id, @RequestBody Empleado empleado) {
        Empleado empleadoActualizado = empleadoServicio.actualizarEmpleado(id, empleado);
        return ResponseEntity.ok(empleadoActualizado);
    }

    @PreAuthorize("hasAuthority('empleados:eliminar')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEmpleado(@PathVariable Long id) {
        empleadoServicio.eliminarEmpleado(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('empleados:ver')")
    @GetMapping("/departamento/{departamento}")
    public ResponseEntity<List<Empleado>> obtenerEmpleadosPorDepartamento(@PathVariable String departamento) {
        List<Empleado> empleados = empleadoServicio.obtenerEmpleadosPorDepartamento(departamento);
        return ResponseEntity.ok(empleados);
    }

    @PreAuthorize("hasAuthority('empleados:ver')")
    @GetMapping("/sueldo-mayor-a/{sueldo}")
    public ResponseEntity<List<Empleado>> obtenerEmpleadosConSueldoMayorA(@PathVariable Double sueldo) {
        List<Empleado> empleados = empleadoServicio.obtenerEmpleadosConSueldoMayorA(sueldo);
        return ResponseEntity.ok(empleados);
    }

    @PreAuthorize("hasAuthority('empleados:ver')")
    @GetMapping("/test")
    public String test() {
        return "ok";
    }
}