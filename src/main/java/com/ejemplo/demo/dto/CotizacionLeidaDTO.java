package com.ejemplo.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos leídos de un PDF de Siigo. NO se guarda nada: el frontend usa esto para
 * llenar el formulario, el usuario lo revisa y luego crea la cotización con POST.
 *
 * avisos: datos que no se pudieron leer o valores que no cuadran.
 * idExistente: si ya hay una cotización con ese número, su id (para avisar o abrirla).
 */
public record CotizacionLeidaDTO(
        String numero,
        String cliente,
        String ingSolicitante,
        List<ItemCotizacionDTO> items,
        BigDecimal subtotal,
        BigDecimal iva,
        BigDecimal total,
        List<String> avisos,
        Long idExistente
) {
}
