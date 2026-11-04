package com.ejemplo.demo.controlador;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.ejemplo.demo.dto.ActualizarUsuarioRequest;
import com.ejemplo.demo.dto.AsignarRolRequest;
import com.ejemplo.demo.dto.CambiarEstadoRequest;
import com.ejemplo.demo.dto.CrearUsuarioRequest;
import com.ejemplo.demo.dto.UsuarioDTO;
import com.ejemplo.demo.servicio.UsuarioServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "Usuarios", description = "Crear, editar, activar/desactivar usuarios y asignarles rol. Requiere usuarios:gestionar.")
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasAuthority('usuarios:gestionar')") // aplica a todos los endpoints
public class UsuarioControlador {

    private final UsuarioServicio servicio;

    public UsuarioControlador(UsuarioServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<UsuarioDTO> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    public UsuarioDTO obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<UsuarioDTO> crear(@RequestBody CrearUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(request));
    }

    @PutMapping("/{id}")
    public UsuarioDTO actualizar(@PathVariable Long id, @RequestBody ActualizarUsuarioRequest request) {
        return servicio.actualizar(id, request);
    }

    /** Activar / desactivar: { "activo": false } */
    @PatchMapping("/{id}/activo")
    public UsuarioDTO cambiarEstado(@PathVariable Long id, @RequestBody CambiarEstadoRequest request) {
        if (request.activo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo 'activo' es obligatorio");
        }
        return servicio.cambiarEstado(id, request.activo());
    }

    /** Asignar rol: { "rolId": 2 }  —  { "rolId": null } quita el rol */
    @PutMapping("/{id}/rol")
    public UsuarioDTO asignarRol(@PathVariable Long id, @RequestBody AsignarRolRequest request) {
        return servicio.asignarRol(id, request.rolId());
    }
}
