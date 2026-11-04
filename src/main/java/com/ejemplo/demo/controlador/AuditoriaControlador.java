package com.ejemplo.demo.controlador;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.ejemplo.demo.dto.AuditoriaDTO;
import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.servicio.AuditoriaServicio;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Solo lectura: la auditoría no se puede modificar ni borrar desde la API. */
@Tag(name = "Auditoría", description = "Consulta del registro de auditoría (solo lectura). Requiere auditoria:ver.")
@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasAuthority('auditoria:ver')")
public class AuditoriaControlador {

    private final AuditoriaServicio auditoriaServicio;

    public AuditoriaControlador(AuditoriaServicio auditoriaServicio) {
        this.auditoriaServicio = auditoriaServicio;
    }

    /**
     * Ejemplos:
     *   GET /api/auditoria
     *   GET /api/auditoria?entidad=USUARIO&entidadId=5
     *   GET /api/auditoria?actor=admin&desde=2026-11-01&hasta=2026-11-30&pagina=0&tamano=20
     */
    @GetMapping
    public PaginaDTO<AuditoriaDTO> buscar(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String entidadId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        return auditoriaServicio.buscar(entidad, entidadId, actor, accion, desde, hasta, pagina, tamano);
    }
}
