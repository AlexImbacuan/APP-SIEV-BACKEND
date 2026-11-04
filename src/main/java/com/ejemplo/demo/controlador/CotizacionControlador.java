package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.CambiarEstadoCotizacionRequest;
import com.ejemplo.demo.dto.CotizacionDTO;
import com.ejemplo.demo.dto.CotizacionLeidaDTO;
import com.ejemplo.demo.dto.CotizacionRequest;
import com.ejemplo.demo.dto.CotizacionResumenDTO;
import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.dto.ResponsableDTO;
import com.ejemplo.demo.modelo.EstadoCotizacion;
import com.ejemplo.demo.servicio.CotizacionServicio;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Tag(name = "Cotizaciones", description = "Cotizaciones a clientes, a mano o leyendo el PDF de Siigo. "
        + "Requiere cotizaciones:ver / crear / editar / aprobar / eliminar.")
@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionControlador {

    private final CotizacionServicio servicio;

    public CotizacionControlador(CotizacionServicio servicio) {
        this.servicio = servicio;
    }

    /**
     * Ejemplos:
     *   GET /api/cotizaciones
     *   GET /api/cotizaciones?estado=PENDIENTE&responsableId=3
     *   GET /api/cotizaciones?facturada=false&texto=bucanero&pagina=0&tamano=20
     */
    @GetMapping
    @PreAuthorize("hasAuthority('cotizaciones:ver')")
    public PaginaDTO<CotizacionResumenDTO> buscar(
            @RequestParam(required = false) EstadoCotizacion estado,
            @RequestParam(required = false) Long responsableId,
            @RequestParam(required = false) Boolean facturada,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.buscar(estado, responsableId, facturada, texto, pagina, tamano);
    }

    /** Usuarios que se pueden escoger como responsable SIEV (activos y con rol). */
    @GetMapping("/responsables")
    @PreAuthorize("hasAuthority('cotizaciones:ver')")
    public List<ResponsableDTO> responsables() {
        return servicio.responsables();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cotizaciones:ver')")
    public CotizacionDTO obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /**
     * Lee el PDF de una cotización de Siigo y devuelve sus datos para llenar el formulario.
     * NO guarda nada (ni el archivo ni la cotización): luego se crea con POST /api/cotizaciones.
     * Enviar como multipart/form-data con el campo "archivo".
     */
    @PostMapping(value = "/leer-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('cotizaciones:crear')")
    public CotizacionLeidaDTO leerPdf(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes adjuntar el PDF en el campo 'archivo'");
        }
        try {
            return servicio.leerPdf(archivo.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo enviado");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cotizaciones:crear')")
    public ResponseEntity<CotizacionDTO> crear(@RequestBody CotizacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(request));
    }

    /** Reemplaza todos los datos, incluidos los materiales. No cambia el estado. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cotizaciones:editar')")
    public CotizacionDTO actualizar(@PathVariable Long id, @RequestBody CotizacionRequest request) {
        return servicio.actualizar(id, request);
    }

    /** { "estado": "APROBADA" }  o  { "estado": "NO_APROBADA", "motivoRechazo": "Precio alto" } */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('cotizaciones:aprobar')")
    public CotizacionDTO cambiarEstado(@PathVariable Long id, @RequestBody CambiarEstadoCotizacionRequest request) {
        return servicio.cambiarEstado(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cotizaciones:eliminar')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
