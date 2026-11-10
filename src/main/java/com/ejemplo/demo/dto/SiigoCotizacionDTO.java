package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.SiigoCotizacion;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cotización copiada de Siigo. items solo viene en el detalle (null en la lista). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SiigoCotizacionDTO(
        Long id,
        String siigoId,
        String numero,
        Integer consecutivo,
        LocalDate fecha,
        String clienteNit,
        Long vendedorId,
        BigDecimal total,
        int cantidadItems,
        String urlPublica,
        LocalDateTime creadoEnSiigo,
        LocalDateTime actualizadoEnSiigo,
        boolean eliminadaEnSiigo,
        LocalDateTime sincronizadoEn,
        JsonNode items
) {

    public static SiigoCotizacionDTO desde(SiigoCotizacion c, JsonNode items) {
        return new SiigoCotizacionDTO(c.getId(), c.getSiigoId(), c.getNumero(), c.getConsecutivo(),
                c.getFecha(), c.getClienteNit(), c.getVendedorId(), c.getTotal(), c.getCantidadItems(),
                c.getUrlPublica(), c.getCreadoEnSiigo(), c.getActualizadoEnSiigo(), c.isEliminadaEnSiigo(),
                c.getSincronizadoEn(), items);
    }
}
