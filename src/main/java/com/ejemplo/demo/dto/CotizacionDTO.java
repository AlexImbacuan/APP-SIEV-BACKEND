package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Cotizacion;
import com.ejemplo.demo.modelo.EstadoCotizacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle completo de una cotización.
 * avisos: inconsistencias en los valores (ej. la suma de los materiales no da el subtotal).
 * Son solo advertencias; la cotización se guarda igual.
 */
public record CotizacionDTO(
        Long id,
        String numero,
        String cliente,
        String ingSolicitante,
        EstadoCotizacion estado,
        String motivoRechazo,
        ResponsableDTO responsable,
        List<ItemCotizacionDTO> items,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        String numeroFactura,
        boolean facturada,
        String notas,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        List<String> avisos
) {

    public static CotizacionDTO desde(Cotizacion c, List<String> avisos) {
        return new CotizacionDTO(
                c.getId(),
                c.getNumero(),
                c.getCliente(),
                c.getIngSolicitante(),
                c.getEstado(),
                c.getMotivoRechazo(),
                ResponsableDTO.desde(c.getResponsable()),
                c.getItems().stream().map(ItemCotizacionDTO::desde).toList(),
                c.getSubtotal(),
                c.getIva(),
                c.getTotal(),
                c.getNumeroFactura(),
                c.estaFacturada(),
                c.getNotas(),
                c.getCreadoEn(),
                c.getActualizadoEn(),
                avisos
        );
    }
}
