package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.ControlSiigoCotizacionRequest;
import com.ejemplo.demo.dto.EstadoSiigoCotizacionRequest;
import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.dto.ResponsableDTO;
import com.ejemplo.demo.dto.SiigoCotizacionDTO;
import com.ejemplo.demo.modelo.EstadoSiigoCotizacion;
import com.ejemplo.demo.modelo.SincronizacionSiigo;
import com.ejemplo.demo.servicio.CotizacionServicio;
import com.ejemplo.demo.servicio.SiigoCotizacionServicio;
import com.ejemplo.demo.servicio.SiigoSincronizacionServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Cotizaciones de Siigo (copia local) con su control SIEV.
 * Leer: siigo:explorar. Editar control y estado: siigo:editar.
 * Las consultas NO llaman a Siigo: leen la copia, que se actualiza sola o con POST /sincronizar.
 */
@Tag(name = "Siigo - cotizaciones", description = "Cotizaciones de Siigo con el control SIEV (estado, OC, "
        + "factura, pagos, responsable). Leer: siigo:explorar. Editar: siigo:editar.")
@RestController
@RequestMapping("/api/siigo-cotizaciones")
public class SiigoCotizacionControlador {

    private final SiigoCotizacionServicio servicio;
    private final SiigoSincronizacionServicio sincronizacion;
    private final CotizacionServicio cotizacionServicio;

    public SiigoCotizacionControlador(SiigoCotizacionServicio servicio, SiigoSincronizacionServicio sincronizacion,
                                      CotizacionServicio cotizacionServicio) {
        this.servicio = servicio;
        this.sincronizacion = sincronizacion;
        this.cotizacionServicio = cotizacionServicio;
    }

    /**
     * Ejemplos:
     *   GET /api/siigo-cotizaciones?pagina=0&tamano=25
     *   GET /api/siigo-cotizaciones?estado=POR_FACTURAR&responsableId=3
     *   GET /api/siigo-cotizaciones?texto=cargill&desde=2026-10-01&hasta=2026-10-31
     */
    @Operation(summary = "Buscar (texto = número, NIT, empresa, descripción, ingeniero, OC o factura)")
    @GetMapping
    @PreAuthorize("hasAuthority('siigo:explorar')")
    public PaginaDTO<SiigoCotizacionDTO> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoSiigoCotizacion estado,
            @RequestParam(required = false) Long responsableId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean incluirEliminadas,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano) {
        return servicio.buscar(texto, estado, responsableId, desde, hasta, incluirEliminadas, pagina, tamano);
    }

    @Operation(summary = "Usuarios que se pueden escoger como responsable SIEV")
    @GetMapping("/responsables")
    @PreAuthorize("hasAuthority('siigo:explorar')")
    public List<ResponsableDTO> responsables() {
        return cotizacionServicio.responsables();
    }

    @Operation(summary = "Detalle con los ítems y el encabezado de Siigo")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('siigo:explorar')")
    public SiigoCotizacionDTO obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @Operation(summary = "Guardar los campos de control SIEV (reemplaza todos; el estado va aparte)")
    @PutMapping("/{id}/control")
    @PreAuthorize("hasAuthority('siigo:editar')")
    public SiigoCotizacionDTO actualizarControl(@PathVariable Long id, @RequestBody ControlSiigoCotizacionRequest r,
                                                Authentication auth) {
        return servicio.actualizarControl(id, r, auth.getName());
    }

    /** { "estado": "POR_EJECUTAR" } */
    @Operation(summary = "Cambiar el estado: POR_APROBAR, POR_EJECUTAR, POR_FACTURAR, PAGADA, NO_APROBADA")
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('siigo:editar')")
    public SiigoCotizacionDTO cambiarEstado(@PathVariable Long id, @RequestBody EstadoSiigoCotizacionRequest r,
                                            Authentication auth) {
        return servicio.cambiarEstado(id, r == null ? null : r.estado(), auth.getName());
    }

    // ------------------------------------------------------------------ sincronización

    @Operation(summary = "Estado: cuántas hay guardadas y la última sincronización")
    @GetMapping("/sincronizacion")
    @PreAuthorize("hasAuthority('siigo:explorar')")
    public Map<String, Object> estado() {
        return sincronizacion.estado();
    }

    @Operation(summary = "Últimas 20 sincronizaciones")
    @GetMapping("/sincronizacion/historial")
    @PreAuthorize("hasAuthority('siigo:explorar')")
    public List<SincronizacionSiigo> historial() {
        return sincronizacion.historial();
    }

    @Operation(summary = "Sincronizar ahora con Siigo (tarda ~15 s)")
    @PostMapping("/sincronizar")
    @PreAuthorize("hasAuthority('siigo:explorar')")
    public SincronizacionSiigo sincronizar(Authentication auth) {
        return sincronizacion.sincronizar("MANUAL " + auth.getName());
    }
}
