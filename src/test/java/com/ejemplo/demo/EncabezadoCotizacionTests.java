package com.ejemplo.demo;

import com.ejemplo.demo.servicio.EncabezadoCotizacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Lectura del encabezado "Señor / ingeniero / sede / Asunto" de las cotizaciones de Siigo. */
class EncabezadoCotizacionTests {

    @Test
    void ejemploReal() {
        var d = EncabezadoCotizacion.interpretar("""
                Señor


                Juan Carlos Soler


                Cargill – Planta Villagorgona


                Asunto: Cotización_2856_Suministro de personal para reparación de lámparas\s
                """);
        assertEquals("Juan Carlos Soler", d.ingeniero());
        assertEquals("Cargill – Planta Villagorgona", d.sede());
        assertEquals("Suministro de personal para reparación de lámparas", d.descripcion());
    }

    @Test
    void enHtml() {
        var d = EncabezadoCotizacion.interpretar(
                "<p>Señor<br>Ing. Ana Pérez<br>Planta Palmira</p><p>Asunto: Cotización 2850 - Tablero</p>");
        assertEquals("Ing. Ana Pérez", d.ingeniero());
        assertEquals("Planta Palmira", d.sede());
        assertEquals("Tablero", d.descripcion());
    }

    @Test
    void sinSede() {
        var d = EncabezadoCotizacion.interpretar("Señora\nMaría Gómez\nAsunto: Cotización_10_Cableado");
        assertEquals("María Gómez", d.ingeniero());
        assertNull(d.sede());
        assertEquals("Cableado", d.descripcion());
    }

    @Test
    void vacioONull() {
        assertTrue(EncabezadoCotizacion.interpretar(null).vacio());
        assertTrue(EncabezadoCotizacion.interpretar("  ").vacio());
        assertTrue(EncabezadoCotizacion.interpretar("Texto sin convención").vacio());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Asunto: Cotización_2856_Suministro de personal | Suministro de personal",
            "Asunto: Cotización 2856 Suministro de personal | Suministro de personal",
            "Asunto: COTIZACION_2856_Suministro_de_personal | Suministro de personal",
            "Asunto: Cotización N° 2856 - Suministro de personal | Suministro de personal",
            "Asunto: Cotización C-1-2856: Suministro de personal | Suministro de personal",
            "Asunto: Cotizacion_Suministro de personal | Suministro de personal",
            "ASUNTO : Suministro de personal | Suministro de personal",
            "Asunto: Cotización_2856_Cambio de 2 reflectores 150W | Cambio de 2 reflectores 150W"
    })
    void variantesDelAsunto(String linea, String esperado) {
        assertEquals(esperado, EncabezadoCotizacion.interpretar(linea).descripcion());
    }
}
