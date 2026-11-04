package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Cotizacion;
import com.ejemplo.demo.modelo.EstadoCotizacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila de la lista de cotizaciones (sin materiales ni notas). */
public record CotizacionResumenDTO(
        Long id,
        String numero,
        String cliente,
        String ingSolicitante,
        EstadoCotizacion estado,
        ResponsableDTO responsable,
        BigDecimal total,
        String numeroFactura,
        boolean facturada,
        LocalDateTime creadoEn
) {

    public static CotizacionResumenDTO desde(Cotizacion c) {
        return new CotizacionResumenDTO(
                c.getId(),
                c.getNumero(),
                c.getCliente(),
                c.getIngSolicitante(),
                c.getEstado(),
                ResponsableDTO.desde(c.getResponsable()),
                c.getTotal(),
                c.getNumeroFactura(),
                c.estaFacturada(),
                c.getCreadoEn()
        );
    }
}
