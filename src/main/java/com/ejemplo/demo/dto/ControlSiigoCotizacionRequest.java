package com.ejemplo.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Campos de control SIEV de una cotización de Siigo. Reemplaza TODOS los campos de control
 * (lo que venga null queda vacío). El estado se cambia aparte con PATCH .../estado.
 *
 * descripcion / ingenieroSolicitante: null o vacío = usar lo leído del encabezado de Siigo.
 */
public record ControlSiigoCotizacionRequest(
        String descripcion,
        String ingenieroSolicitante,
        Long responsableId,
        String ordenCompra,
        BigDecimal valorOc,
        String numeroFactura,
        LocalDate fechaFactura,
        LocalDate fechaPago,
        String comprobantePago,
        BigDecimal valorPagado
) {
}
