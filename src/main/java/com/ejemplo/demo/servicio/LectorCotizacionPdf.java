package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.ItemCotizacionDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el texto de una cotización exportada de Siigo (PDF) y extrae sus datos.
 * El archivo solo se procesa en memoria: nunca se guarda.
 *
 * Formato esperado (texto que extrae PDFBox de las cotizaciones de Siigo):
 * <pre>
 * No. C-1-2562
 * SOLUCIONES INTEGRALES Para : CLIENTE S.A
 * ING: NOMBRE DEL INGENIERO
 * Ítem Descripción Vr. Unitario Cantidad Vr. Bruto
 * 1 Cable encauchetado 4x12 16,600.00 120.00 1,992,000.00
 * ...
 * Subtotal 22,000,000.00
 * IVA 19% 4,180,000.00
 * Total a Pagar 26,180,000.00
 * </pre>
 * Si Siigo cambia su formato, lo que no se pueda leer queda vacío y se informa en avisos;
 * el usuario lo completa a mano antes de guardar.
 */
@Component
public class LectorCotizacionPdf {

    /** Datos extraídos del PDF. Cualquier campo puede ser null si no se encontró. */
    public record Resultado(
            String numero,
            String cliente,
            String ingSolicitante,
            List<ItemCotizacionDTO> items,
            BigDecimal subtotal,
            BigDecimal iva,
            BigDecimal total
    ) {
        boolean vacio() {
            return numero == null && cliente == null && items.isEmpty() && total == null;
        }
    }

    private static final String NUM = "(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)";

    private static final Pattern NUMERO = Pattern.compile("^(?:Cotizaci[óo]n\\s+)?No\\.\\s*(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLIENTE = Pattern.compile("Para\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ING = Pattern.compile("^ING(?:ENIER[OA])?(?:\\.|\\s*:|\\s)\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCABEZADO_TABLA = Pattern.compile("^[ÍI]tem\\s+Descripci[óo]n", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILA = Pattern.compile("^(\\d+)\\s+(.+?)\\s+" + NUM + "\\s+" + NUM + "\\s+" + NUM + "\\s*$");
    private static final Pattern FIN_TABLA = Pattern.compile("^(Total Bruto|Subtotal|IVA\\b|Total a Pagar)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBTOTAL = Pattern.compile("^Subtotal\\s+" + NUM + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IVA = Pattern.compile("^IVA\\b.*?\\s" + NUM + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile("^Total a Pagar\\s+" + NUM + "\\s*$", Pattern.CASE_INSENSITIVE);
    /** Líneas que el navegador agrega al imprimir (fecha/título y "1/3"): se ignoran. */
    private static final Pattern RUIDO = Pattern.compile("(^\\d+/\\d+$)|(- iSiigo$)", Pattern.CASE_INSENSITIVE);

    /** Lee el PDF completo. Lanza 400 si no es un PDF válido o no parece una cotización de Siigo. */
    public Resultado leer(byte[] pdf) {

        if (pdf == null || pdf.length < 5 || !new String(pdf, 0, 5).startsWith("%PDF")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no es un PDF válido");
        }

        String texto;
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper extractor = new PDFTextStripper();
            extractor.setSortByPosition(true);
            texto = extractor.getText(documento);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo leer el PDF (puede estar dañado o protegido con contraseña)");
        }

        Resultado resultado = interpretar(texto);
        if (resultado.vacio()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El PDF no parece una cotización de Siigo: no se encontró número, cliente, materiales ni total");
        }
        return resultado;
    }

    /** Extrae los datos a partir del texto del PDF. Público para poder probarlo sin un PDF real. */
    public Resultado interpretar(String texto) {

        String numero = null;
        String cliente = null;
        String ing = null;
        BigDecimal subtotal = null;
        BigDecimal iva = null;
        BigDecimal total = null;
        List<ItemCotizacionDTO> items = new ArrayList<>();

        boolean enTabla = false;
        String filaPendiente = null; // fila cuya descripción ocupó varias líneas

        for (String crudo : texto.split("\\R")) {
            String linea = crudo.strip();
            if (linea.isEmpty() || RUIDO.matcher(linea).find()) {
                continue;
            }

            Matcher m;

            if (ENCABEZADO_TABLA.matcher(linea).find()) {
                enTabla = true; // la tabla puede continuar en otra página con el encabezado repetido
                continue;
            }

            if (enTabla && FIN_TABLA.matcher(linea).find()) {
                enTabla = false;
                filaPendiente = null;
            }

            if (enTabla) {
                String candidata = filaPendiente != null ? filaPendiente + " " + linea : linea;
                m = FILA.matcher(candidata);
                if (m.matches()) {
                    items.add(new ItemCotizacionDTO(
                            m.group(2).strip(),
                            numero(m.group(4)),
                            numero(m.group(3)),
                            numero(m.group(5))));
                    filaPendiente = null;
                } else if (linea.matches("^\\d+\\s+.*")) {
                    filaPendiente = linea; // empieza una fila cuyos valores vienen en la línea siguiente
                } else if (filaPendiente != null) {
                    filaPendiente = candidata;
                } else if (!items.isEmpty()) {
                    // continuación de la descripción del material anterior
                    ItemCotizacionDTO ultimo = items.remove(items.size() - 1);
                    items.add(new ItemCotizacionDTO(ultimo.descripcion() + " " + linea,
                            ultimo.cantidad(), ultimo.valorUnitario(), ultimo.valorBruto()));
                }
                continue;
            }

            if (numero == null && (m = NUMERO.matcher(linea)).find()) {
                numero = m.group(1);
            } else if (cliente == null && (m = CLIENTE.matcher(linea)).find()) {
                cliente = m.group(1).strip();
            } else if (ing == null && (m = ING.matcher(linea)).find()) {
                ing = m.group(1).strip();
            } else if ((m = SUBTOTAL.matcher(linea)).find()) {
                subtotal = numero(m.group(1));
            } else if ((m = IVA.matcher(linea)).find()) {
                iva = numero(m.group(1));
            } else if ((m = TOTAL.matcher(linea)).find()) {
                total = numero(m.group(1));
            }
        }

        return new Resultado(numero, cliente, ing, items, subtotal, iva, total);
    }

    /**
     * Convierte "1,992,000.00" (formato de Siigo) o "1.992.000,00" (formato colombiano) a BigDecimal.
     * El último separador seguido de 1 o 2 dígitos se toma como decimal.
     */
    static BigDecimal numero(String texto) {
        String t = texto.strip();
        int ultimoPunto = t.lastIndexOf('.');
        int ultimaComa = t.lastIndexOf(',');
        int separadorDecimal = Math.max(ultimoPunto, ultimaComa);

        if (separadorDecimal >= 0 && t.length() - separadorDecimal - 1 <= 2) {
            String entera = t.substring(0, separadorDecimal).replaceAll("[.,]", "");
            return new BigDecimal(entera + "." + t.substring(separadorDecimal + 1));
        }
        return new BigDecimal(t.replaceAll("[.,]", ""));
    }
}
