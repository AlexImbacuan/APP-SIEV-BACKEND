package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.ClienteDTO;
import com.ejemplo.demo.dto.ClienteRequest;
import com.ejemplo.demo.servicio.ClienteServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Clientes", description = "Catálogo de empresas; se cruza con las cotizaciones de Siigo por NIT. "
        + "Ver: clientes:gestionar o siigo:explorar. Crear/editar/eliminar: clientes:gestionar.")
@RestController
@RequestMapping("/api/clientes")
public class ClienteControlador {

    private final ClienteServicio servicio;

    public ClienteControlador(ClienteServicio servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Todos los clientes, con cuántas cotizaciones de Siigo tiene cada uno")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('clientes:gestionar', 'siigo:explorar')")
    public List<ClienteDTO> listar() {
        return servicio.listar();
    }

    @Operation(summary = "NITs que aparecen en las cotizaciones de Siigo y aún no tienen cliente creado")
    @GetMapping("/nits-sin-cliente")
    @PreAuthorize("hasAuthority('clientes:gestionar')")
    public List<ClienteServicio.NitSinCliente> nitsSinCliente() {
        return servicio.nitsSinCliente();
    }

    /** { "nit": "800197463", "nombre": "Cargill de Colombia" } — el NIT acepta puntos y dígito de verificación. */
    @Operation(summary = "Crear cliente")
    @PostMapping
    @PreAuthorize("hasAuthority('clientes:gestionar')")
    public ResponseEntity<ClienteDTO> crear(@RequestBody ClienteRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(r));
    }

    @Operation(summary = "Editar cliente")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:gestionar')")
    public ClienteDTO actualizar(@PathVariable Long id, @RequestBody ClienteRequest r) {
        return servicio.actualizar(id, r);
    }

    @Operation(summary = "Eliminar cliente (las cotizaciones no se borran; solo dejan de mostrar el nombre)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('clientes:gestionar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
