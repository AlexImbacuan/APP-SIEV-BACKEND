package com.ejemplo.demo.servicio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta el encabezado que SIEV escribe en las cotizaciones de Siigo, con esta convención:
 *
 *   Señor
 *   Juan Carlos Soler                        <- ingeniero solicitante
 *   Cargill – Planta Villagorgona            <- sede / planta
 *   Asunto: Cotización_2856_Suministro de personal para reparación de lámparas
 *                                            <- descripción: "Suministro de personal para..."
 *
 * De la línea "Asunto" se quitan la palabra "Cotización" y el número, estén separados por
 * guiones bajos, espacios o guiones.
 */
public final class EncabezadoCotizacion {

    public record Datos(String ingeniero, String sede, String descripcion) {
        public boolean vacio() {
            return ingeniero == null && sede == null && descripcion == null;
        }
    }

    private static final Pattern SALUDO = Pattern.compile(
            "^(se[ñn]or(a|es|as)?|sr(a)?\\.?|ingenier[oa]|ing\\.?|doctor(a)?|dr(a)?\\.?)\\s*[:,]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASUNTO = Pattern.compile("^asunto\\s*[:.\\-]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    /** "Cotización_2856_", "COTIZACION 2856 - ", "Cotización N° C-1-2856: ", "Cotizacion_" ... */
    private static final Pattern PREFIJO_COTIZACION = Pattern.compile(
            "^cotizaci[oó]n[\\s_\\-–—:.]*(?:n[°ºo.]*\\s*)?(?:[a-z]-\\d+-)?\\d*[\\s_\\-–—:.]*",
            Pattern.CASE_INSENSITIVE);

    private EncabezadoCotizacion() {
    }

    public static Datos interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            return new Datos(null, null, null);
        }
        List<String> lineas = lineas(texto);

        String ingeniero = null;
        String sede = null;
        String descripcion = null;

        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            Matcher asunto = ASUNTO.matcher(linea);
            if (descripcion == null && asunto.matches()) {
                descripcion = limpiarAsunto(asunto.group(1));
                continue;
            }
            if (ingeniero == null && SALUDO.matcher(linea).matches()) {
                // La línea siguiente es el ingeniero; la que sigue (si no es el asunto), la sede
                if (i + 1 < lineas.size() && !ASUNTO.matcher(lineas.get(i + 1)).matches()) {
                    ingeniero = lineas.get(i + 1);
                    if (i + 2 < lineas.size() && !ASUNTO.matcher(lineas.get(i + 2)).matches()) {
                        sede = lineas.get(i + 2);
                    }
                }
            }
        }
        return new Datos(recortar(ingeniero, 150), recortar(sede, 200), recortar(descripcion, 500));
    }

    static String limpiarAsunto(String asunto) {
        String sinPrefijo = PREFIJO_COTIZACION.matcher(asunto.strip()).replaceFirst("");
        String limpio = sinPrefijo.replace('_', ' ').replaceAll("\\s+", " ").strip();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Separa en líneas no vacías; acepta texto plano o HTML sencillo (<br>, <p>). */
    private static List<String> lineas(String texto) {
        String plano = texto
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ");
        List<String> salida = new ArrayList<>();
        for (String l : plano.split("\\R")) {
            String s = l.replace(' ', ' ').strip();
            if (!s.isEmpty()) {
                salida.add(s);
            }
        }
        return salida;
    }

    private static String recortar(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
