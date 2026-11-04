package com.ejemplo.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Crear o editar una cotización. El estado no se envía aquí
 * (se crea en PENDIENTE y se cambia con PATCH /api/cotizaciones/{id}/estado).
 *
 * subtotal, iva y total son opcionales: si no vienen se calculan
 * (subtotal = suma de los materiales, iva = 19 % del subtotal, total = subtotal + iva).
 */
public record CotizacionRequest(
        String numero,
        String cliente,
        String ingSolicitante,
        Long responsableId,
        List<ItemCotizacionDTO> items,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        String numeroFactura,
        String notas
) {
}
