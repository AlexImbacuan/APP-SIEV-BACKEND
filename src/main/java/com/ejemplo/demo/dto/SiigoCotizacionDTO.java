package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.EstadoSiigoCotizacion;
import com.ejemplo.demo.modelo.SiigoCotizacion;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cotización de Siigo con su control SIEV.
 * - descripcion / ingenieroSolicitante: lo escrito por el usuario o, si no hay, lo leído de Siigo.
 *   descripcionSiigo / ingenieroSiigo traen siempre lo de Siigo (para mostrar "restaurar").
 * - empresa: nombre del cliente con ese NIT en el catálogo de clientes (null si no está creado).
 * - items y encabezadoSiigo solo vienen en el detalle (null en la lista).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SiigoCotizacionDTO(
        Long id,
        String siigoId,
        String numero,
        Integer consecutivo,
        LocalDate fecha,
        String clienteNit,
        String empresa,
        String sede,
        Long vendedorId,
        String descripcion,
        String descripcionSiigo,
        String ingenieroSolicitante,
        String ingenieroSiigo,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        int cantidadItems,
        EstadoSiigoCotizacion estado,
        ResponsableDTO responsable,
        String ordenCompra,
        BigDecimal valorOc,
        String numeroFactura,
        LocalDate fechaFactura,
        LocalDate fechaPago,
        String comprobantePago,
        BigDecimal valorPagado,
        String urlPublica,
        LocalDateTime creadoEnSiigo,
        LocalDateTime actualizadoEnSiigo,
        boolean eliminadaEnSiigo,
        LocalDateTime sincronizadoEn,
        LocalDateTime controlActualizadoEn,
        String controlActualizadoPor,
        String encabezadoSiigo,
        JsonNode items
) {

    public static SiigoCotizacionDTO desde(SiigoCotizacion c, String empresa, boolean detalle, JsonNode items) {
        return new SiigoCotizacionDTO(c.getId(), c.getSiigoId(), c.getNumero(), c.getConsecutivo(), c.getFecha(),
                c.getClienteNit(), empresa, c.getSedeSiigo(), c.getVendedorId(),
                c.getDescripcion() != null ? c.getDescripcion() : c.getDescripcionSiigo(), c.getDescripcionSiigo(),
                c.getIngenieroSolicitante() != null ? c.getIngenieroSolicitante() : c.getIngenieroSiigo(),
                c.getIngenieroSiigo(),
                c.getSubtotal(), c.getIva(), c.getTotal(), c.getCantidadItems(), c.getEstado(),
                c.getResponsable() == null ? null : ResponsableDTO.desde(c.getResponsable()),
                c.getOrdenCompra(), c.getValorOc(), c.getNumeroFactura(), c.getFechaFactura(), c.getFechaPago(),
                c.getComprobantePago(), c.getValorPagado(), c.getUrlPublica(), c.getCreadoEnSiigo(),
                c.getActualizadoEnSiigo(), c.isEliminadaEnSiigo(), c.getSincronizadoEn(),
                c.getControlActualizadoEn(), c.getControlActualizadoPor(),
                detalle ? c.getEncabezadoSiigo() : null, items);
    }
}
