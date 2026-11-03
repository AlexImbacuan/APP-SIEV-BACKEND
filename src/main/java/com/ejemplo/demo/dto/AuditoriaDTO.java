package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Auditoria;

import java.time.LocalDateTime;

public record AuditoriaDTO(
        Long id,
        LocalDateTime fecha,
        String actor,
        String accion,
        String entidad,
        String entidadId,
        String detalle
) {

    public static AuditoriaDTO desde(Auditoria a) {
        return new AuditoriaDTO(a.getId(), a.getFecha(), a.getActor(), a.getAccion(),
                a.getEntidad(), a.getEntidadId(), a.getDetalle());
    }
}
