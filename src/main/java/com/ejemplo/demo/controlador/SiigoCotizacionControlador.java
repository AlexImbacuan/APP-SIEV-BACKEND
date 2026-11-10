package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.dto.SiigoCotizacionDTO;
import com.ejemplo.demo.modelo.SincronizacionSiigo;
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
 * Cotizaciones de Siigo copiadas a la base de datos local. Las consultas NO llaman a Siigo:
 * leen la copia, que se actualiza sola dos veces al día o con POST /sincronizar.
 */
@Tag(name = "Siigo - cotizaciones sincronizadas", description = "Copia local de las cotizaciones de Siigo. "
        + "Requiere siigo:explorar.")
@RestController
@RequestMapping("/api/siigo-cotizaciones")
@PreAuthorize("hasAuthority('siigo:explorar')")
public class SiigoCotizacionControlador {

    private final SiigoSincronizacionServicio servicio;

    public SiigoCotizacionControlador(SiigoSincronizacionServicio servicio) {
        this.servicio = servicio;
    }

    /**
     * Ejemplos:
     *   GET /api/siigo-cotizaciones?pagina=0&tamano=25
     *   GET /api/siigo-cotizaciones?texto=800197463&desde=2026-10-01&hasta=2026-10-31
     */
    @Operation(summary = "Buscar en la copia local (texto = número o NIT)")
    @GetMapping
    public PaginaDTO<SiigoCotizacionDTO> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean incluirEliminadas,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano) {
        return servicio.buscar(texto, desde, hasta, incluirEliminadas, pagina, tamano);
    }

    @Operation(summary = "Detalle con los ítems")
    @GetMapping("/{id}")
    public SiigoCotizacionDTO obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @Operation(summary = "Estado: cuántas hay guardadas y la última sincronización")
    @GetMapping("/sincronizacion")
    public Map<String, Object> estado() {
        return servicio.estado();
    }

    @Operation(summary = "Últimas 20 sincronizaciones")
    @GetMapping("/sincronizacion/historial")
    public List<SincronizacionSiigo> historial() {
        return servicio.historial();
    }

    @Operation(summary = "Sincronizar ahora con Siigo (tarda ~15 s)")
    @PostMapping("/sincronizar")
    public SincronizacionSiigo sincronizar(Authentication auth) {
        return servicio.sincronizar("MANUAL " + auth.getName());
    }
}
