package com.ejemplo.demo;

import com.ejemplo.demo.dto.ItemCotizacionDTO;
import com.ejemplo.demo.servicio.LectorCotizacionPdf;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Lectura de cotizaciones de Siigo (sin Spring: solo el lector). */
class LectorCotizacionPdfTests {

    private final LectorCotizacionPdf lector = new LectorCotizacionPdf();

    @Test
    void leePdfConFormatoSiigo_variasPaginas() {
        LectorCotizacionPdf.Resultado r = lector.leer(PdfCotizacionDePrueba.generar());

        assertEquals("C-1-9001", r.numero());
        assertEquals("CLIENTE DE PRUEBA S.A", r.cliente());
        assertEquals("ANA PEREZ", r.ingSolicitante());
        assertEquals(4, r.items().size());

        ItemCotizacionDTO primero = r.items().get(0);
        assertEquals("Cable encauchetado 3x12", primero.descripcion());
        assertEquals(0, new BigDecimal("100").compareTo(primero.cantidad()));
        assertEquals(0, new BigDecimal("10000").compareTo(primero.valorUnitario()));
        assertEquals(0, new BigDecimal("1000000").compareTo(primero.valorBruto()));

        assertEquals("Selector de 2 posiciones", r.items().get(2).descripcion());
        assertEquals("MANO DE OBRA", r.items().get(3).descripcion());

        assertEquals(0, new BigDecimal("2000000").compareTo(r.subtotal()));
        assertEquals(0, new BigDecimal("380000").compareTo(r.iva()));
        assertEquals(0, new BigDecimal("2380000").compareTo(r.total()));
    }

    @Test
    void descripcionEnVariasLineas_seUne() {
        String texto = String.join("\n",
                "No. C-1-1",
                "Ítem Descripción Vr. Unitario Cantidad Vr. Bruto",
                "1 Suministro e instalación de tomas eléctricos 110,000.00 2.00 220,000.00",
                "de 110V AC para equipos",
                "2 Cable muy largo que ocupa",
                "dos líneas 1,000.00 3.00 3,000.00",
                "Subtotal 223,000.00");

        List<ItemCotizacionDTO> items = lector.interpretar(texto).items();

        assertEquals(2, items.size());
        assertEquals("Suministro e instalación de tomas eléctricos de 110V AC para equipos", items.get(0).descripcion());
        assertEquals("Cable muy largo que ocupa dos líneas", items.get(1).descripcion());
        assertEquals(0, new BigDecimal("3000").compareTo(items.get(1).valorBruto()));
    }

    @Test
    void numerosEnFormatoColombiano_seLeenBien() {
        String texto = String.join("\n",
                "Ítem Descripción Vr. Unitario Cantidad Vr. Bruto",
                "1 Breaker 35.000,00 2,00 70.000,00",
                "Subtotal 70.000,00");

        LectorCotizacionPdf.Resultado r = lector.interpretar(texto);

        assertEquals(0, new BigDecimal("35000").compareTo(r.items().get(0).valorUnitario()));
        assertEquals(0, new BigDecimal("2").compareTo(r.items().get(0).cantidad()));
        assertEquals(0, new BigDecimal("70000").compareTo(r.subtotal()));
    }

    @Test
    void textoSinDatos_devuelveCamposVacios() {
        LectorCotizacionPdf.Resultado r = lector.interpretar("Hola\nNota: esto no es una cotización");
        assertNull(r.numero());
        assertNull(r.cliente());
        assertTrue(r.items().isEmpty());
    }

    @Test
    void archivoQueNoEsPdf_lanza400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> lector.leer("esto no es un pdf".getBytes()));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void pdfSinCotizacion_lanza400() {
        byte[] pdf = PdfCotizacionDePrueba.generar(List.of(List.of("Un documento cualquiera")));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> lector.leer(pdf));
        assertEquals(400, ex.getStatusCode().value());
    }

    /**
     * Prueba con las cotizaciones reales de Siigo que haya en "src/modelo de cotizacion/"
     * (carpeta ignorada por git). En otros equipos o en Docker se omite.
     */
    @Test
    void cotizacionRealLocal_cuadraConSusTotales() throws Exception {
        File[] pdfs = new File("src/modelo de cotizacion").listFiles((d, n) -> n.toLowerCase().endsWith(".pdf"));
        assumeTrue(pdfs != null && pdfs.length > 0, "No hay cotizaciones reales locales");

        for (File pdf : pdfs) {
            LectorCotizacionPdf.Resultado r = lector.leer(Files.readAllBytes(pdf.toPath()));

            assertNotNull(r.numero(), pdf.getName());
            assertNotNull(r.cliente(), pdf.getName());
            assertNotNull(r.ingSolicitante(), pdf.getName());
            assertFalse(r.items().isEmpty(), pdf.getName());
            assertNotNull(r.subtotal(), pdf.getName());
            assertNotNull(r.iva(), pdf.getName());
            assertNotNull(r.total(), pdf.getName());

            BigDecimal suma = r.items().stream()
                    .map(ItemCotizacionDTO::valorBruto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, suma.compareTo(r.subtotal()), pdf.getName() + ": la suma de materiales no da el subtotal");
            assertEquals(0, r.subtotal().add(r.iva()).compareTo(r.total()), pdf.getName() + ": subtotal + IVA != total");
        }
    }
}
