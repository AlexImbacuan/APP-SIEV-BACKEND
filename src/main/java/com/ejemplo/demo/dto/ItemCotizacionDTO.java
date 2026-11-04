package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.ItemCotizacion;

import java.math.BigDecimal;

/**
 * Un material de la cotización. Se usa tanto para enviar como para recibir.
 * Al enviar, valorBruto es opcional: si no viene se calcula como cantidad x valorUnitario.
 */
public record ItemCotizacionDTO(
        String descripcion,
        BigDecimal cantidad,
        BigDecimal valorUnitario,
        BigDecimal valorBruto
) {

    public static ItemCotizacionDTO desde(ItemCotizacion item) {
        return new ItemCotizacionDTO(item.getDescripcion(), item.getCantidad(),
                item.getValorUnitario(), item.getValorBruto());
    }
}
