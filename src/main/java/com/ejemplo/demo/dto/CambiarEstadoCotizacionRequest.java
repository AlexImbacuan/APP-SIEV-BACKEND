package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.EstadoCotizacion;

/** { "estado": "NO_APROBADA", "motivoRechazo": "..." }. El motivo es obligatorio solo para NO_APROBADA. */
public record CambiarEstadoCotizacionRequest(EstadoCotizacion estado, String motivoRechazo) {
}
