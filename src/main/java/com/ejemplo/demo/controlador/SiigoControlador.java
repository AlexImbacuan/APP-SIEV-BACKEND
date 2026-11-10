package com.ejemplo.demo.controlador;

import com.ejemplo.demo.servicio.SiigoCliente;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Endpoints TEMPORALES para explorar la API de Siigo. Devuelven el JSON de Siigo sin
 * transformar ni guardar nada. Solo el SUPERADMIN tiene el permiso siigo:explorar
 * (no está en los permisos por defecto de ningún otro rol).
 */
@Tag(name = "Siigo (exploración)", description = "Consulta directa y temporal a la API de Siigo Nube. "
        + "Requiere siigo:explorar.")
@RestController
@RequestMapping("/api/siigo")
@PreAuthorize("hasAuthority('siigo:explorar')")
public class SiigoControlador {

    private final SiigoCliente siigo;

    public SiigoControlador(SiigoCliente siigo) {
        this.siigo = siigo;
    }

    @Operation(summary = "Recursos de Siigo que se pueden consultar")
    @GetMapping("/recursos")
    public Set<String> recursos() {
        return new TreeSet<>(SiigoCliente.RECURSOS);
    }

    /**
     * Una página, tal como la devuelve Siigo. Los parámetros se reenvían, ej:
     *   GET /api/siigo/quotations?page=1&page_size=25
     *   GET /api/siigo/quotations?created_start=2026-01-01
     */
    @Operation(summary = "Una página de un recurso (los parámetros se reenvían a Siigo)")
    @GetMapping("/{recurso}")
    public JsonNode listar(@PathVariable String recurso, @RequestParam Map<String, String> filtros) {
        return siigo.listar(recurso, filtros);
    }

    /**
     * Todas las páginas juntas. Ej:
     *   GET /api/siigo/quotations/todo?resumen=true          (sin ítems, liviano)
     *   GET /api/siigo/quotations/todo?refrescar=true        (ignora la caché de 10 min)
     * Cualquier otro parámetro se reenvía a Siigo como filtro.
     */
    @Operation(summary = "Todos los registros de un recurso (páginas en paralelo, caché de 10 min). "
            + "resumen=true quita los ítems; refrescar=true ignora la caché")
    @GetMapping("/{recurso}/todo")
    public JsonNode listarTodo(@PathVariable String recurso,
                               @RequestParam(defaultValue = "false") boolean resumen,
                               @RequestParam(defaultValue = "false") boolean refrescar,
                               @RequestParam Map<String, String> filtros) {
        Map<String, String> paraSiigo = new LinkedHashMap<>(filtros);
        paraSiigo.remove("resumen");
        paraSiigo.remove("refrescar");
        return siigo.listarTodo(recurso, paraSiigo, resumen, refrescar);
    }

    @Operation(summary = "Un registro por su id de Siigo")
    @GetMapping("/{recurso}/id/{id}")
    public JsonNode obtener(@PathVariable String recurso, @PathVariable String id) {
        return siigo.obtener(recurso, id);
    }
}
