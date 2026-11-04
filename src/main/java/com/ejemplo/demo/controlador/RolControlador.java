package com.ejemplo.demo.controlador;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.ejemplo.demo.dto.ActualizarPermisosRequest;
import com.ejemplo.demo.dto.CrearRolRequest;
import com.ejemplo.demo.dto.PermisoDTO;
import com.ejemplo.demo.dto.RolDTO;
import com.ejemplo.demo.servicio.RolServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Roles y permisos", description = "Crear roles y asignarles permisos. Requiere roles:gestionar.")
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('roles:gestionar')") // aplica a todos los endpoints
public class RolControlador {

    private final RolServicio rolServicio;

    public RolControlador(RolServicio rolServicio) {
        this.rolServicio = rolServicio;
    }

    @GetMapping("/roles")
    public List<RolDTO> listarRoles() {
        return rolServicio.listarRoles();
    }

    @PostMapping("/roles")
    public ResponseEntity<RolDTO> crearRol(@RequestBody CrearRolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rolServicio.crearRol(request));
    }

    /** Reemplaza los permisos del rol: { "permisos": ["proyectos:ver", "empleados:ver"] } */
    @PutMapping("/roles/{id}/permisos")
    public RolDTO actualizarPermisos(@PathVariable Long id, @RequestBody ActualizarPermisosRequest request) {
        return rolServicio.actualizarPermisos(id, request.permisos());
    }

    @GetMapping("/permisos")
    public List<PermisoDTO> listarPermisos() {
        return rolServicio.listarPermisos();
    }
}
