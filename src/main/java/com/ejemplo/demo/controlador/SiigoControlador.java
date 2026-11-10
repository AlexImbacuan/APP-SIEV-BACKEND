package com.ejemplo.demo.controlador;

import com.ejemplo.demo.servicio.SiigoCliente;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    /** Recorre todas las páginas (de a 100) y junta los resultados. Ej: GET /api/siigo/quotations/todo */
    @Operation(summary = "Todos los registros de un recurso, recorriendo las páginas (máx. 50 páginas)")
    @GetMapping("/{recurso}/todo")
    public JsonNode listarTodo(@PathVariable String recurso, @RequestParam Map<String, String> filtros) {
        return siigo.listarTodo(recurso, filtros);
    }

    @Operation(summary = "Un registro por su id de Siigo")
    @GetMapping("/{recurso}/id/{id}")
    public JsonNode obtener(@PathVariable String recurso, @PathVariable String id) {
        return siigo.obtener(recurso, id);
    }
}
