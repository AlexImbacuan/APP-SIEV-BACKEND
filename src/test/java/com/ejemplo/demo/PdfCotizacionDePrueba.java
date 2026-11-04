package com.ejemplo.demo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Genera PDFs con el mismo formato de texto que las cotizaciones de Siigo, con datos inventados.
 * Así los tests no dependen de documentos reales de clientes.
 */
final class PdfCotizacionDePrueba {

    private PdfCotizacionDePrueba() {
    }

    /** Página 1 con encabezado y parte de la tabla; página 2 con el resto y los totales. */
    static final List<List<String>> PAGINAS = List.of(
            List.of(
                    "1/1/26, 9:00 a.m. Cotización - iSiigo",
                    "Cotización",
                    "No. C-1-9001",
                    "EMPRESA DE PRUEBA S.A.S. Para : CLIENTE DE PRUEBA S.A",
                    "NIT 900.000.000-0 Nit : 800.000.000-1",
                    "Calle 1 # 2-3 Fecha : 2026-01-15",
                    "SEÑORES CLIENTE DE PRUEBA",
                    "ING: ANA PEREZ",
                    "ASUNTO: COTIZACIÓN 9001 MONTAJE DE TABLERO DE PRUEBA.",
                    "Ítem Descripción Vr. Unitario Cantidad Vr. Bruto",
                    "1 Cable encauchetado 3x12 10,000.00 100.00 1,000,000.00",
                    "2 Tubería IMC 3/4 50,000.00 10.00 500,000.00",
                    "1/2"),
            List.of(
                    "1/1/26, 9:00 a.m. Cotización - iSiigo",
                    "Ítem Descripción Vr. Unitario Cantidad Vr. Bruto",
                    "3 Selector de 2 posiciones 70,000.00 1.00 70,000.00",
                    "4 MANO DE OBRA 430,000.00 1.00 430,000.00",
                    "Total Bruto 2,000,000.00",
                    "Subtotal 2,000,000.00",
                    "IVA 19% 380,000.00",
                    "Total a Pagar 2,380,000.00",
                    "TIEMPO DE ENTREGA 10 DÍAS",
                    "2/2"));

    static byte[] generar() {
        return generar(PAGINAS);
    }

    static byte[] generar(List<List<String>> paginas) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDType1Font fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> lineas : paginas) {
                PDPage pagina = new PDPage();
                documento.addPage(pagina);
                try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                    float y = 750;
                    for (String linea : lineas) {
                        contenido.beginText();
                        contenido.setFont(fuente, 10);
                        contenido.newLineAtOffset(40, y);
                        contenido.showText(linea);
                        contenido.endText();
                        y -= 16;
                    }
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
