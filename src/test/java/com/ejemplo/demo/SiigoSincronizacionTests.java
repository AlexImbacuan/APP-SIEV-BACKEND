package com.ejemplo.demo;

import com.ejemplo.demo.modelo.SincronizacionSiigo;
import com.ejemplo.demo.repositorio.SiigoCotizacionRepositorio;
import com.ejemplo.demo.repositorio.SincronizacionSiigoRepositorio;
import com.ejemplo.demo.servicio.SiigoCliente;
import com.ejemplo.demo.servicio.SiigoSincronizacionServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sincronización de cotizaciones de Siigo a la tabla local (Siigo simulado). */
@SpringBootTest
@AutoConfigureMockMvc
class SiigoSincronizacionTests {

    @MockitoBean
    private SiigoCliente siigo;

    @Autowired
    private SiigoSincronizacionServicio servicio;

    @Autowired
    private SiigoCotizacionRepositorio cotizaciones;

    @Autowired
    private SincronizacionSiigoRepositorio sincronizaciones;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiar() {
        cotizaciones.deleteAll();
        sincronizaciones.deleteAll();
    }

    @Test
    void sincroniza_nuevas_actualizadas_y_eliminadas() throws Exception {
        siigoDevuelve(false,
                cotizacion("a", "C-1-1", 1, "2026-11-01", 1000, "2026-11-01T10:00:00"),
                cotizacion("b", "C-1-2", 2, "2026-11-02", 2000, "2026-11-02T10:00:00"));
        SincronizacionSiigo primera = servicio.sincronizar("MANUAL");
        assertTrue(primera.isExitosa());
        assertEquals(2, primera.getNuevas());
        assertEquals(2, cotizaciones.count());

        // Sin cambios: no cuenta actualizadas
        SincronizacionSiigo igual = servicio.sincronizar("MANUAL");
        assertEquals(0, igual.getNuevas());
        assertEquals(0, igual.getActualizadas());

        // "a" cambia de total, "b" desaparece de Siigo, "c" es nueva
        siigoDevuelve(false,
                cotizacion("a", "C-1-1", 1, "2026-11-01", 1500, "2026-11-03T08:00:00.123"),
                cotizacion("c", "C-1-3", 3, "2026-11-03", 3000, "2026-11-03T09:00:00"));
        SincronizacionSiigo segunda = servicio.sincronizar("AUTOMATICA");
        assertEquals(1, segunda.getNuevas());
        assertEquals(1, segunda.getActualizadas());
        assertEquals(1, segunda.getEliminadas());

        var lista = servicio.buscar(null, null, null, false, 0, 25);
        assertEquals(2, lista.totalElementos()); // "b" queda oculta
        assertEquals("C-1-3", lista.contenido().get(0).numero()); // más reciente primero
        assertEquals(3, servicio.buscar(null, null, null, true, 0, 25).totalElementos());

        var a = servicio.buscar("C-1-1", null, null, false, 0, 25).contenido().get(0);
        assertEquals(0, a.total().compareTo(new java.math.BigDecimal("1500")));
        var detalle = servicio.obtener(a.id());
        assertEquals(1, detalle.items().size());
        assertEquals("Mano de obra", detalle.items().get(0).path("description").asText());
    }

    @Test
    void consultaIncompleta_noMarcaEliminadas() throws Exception {
        siigoDevuelve(false, cotizacion("a", "C-1-1", 1, "2026-11-01", 1000, null),
                cotizacion("b", "C-1-2", 2, "2026-11-02", 2000, null));
        servicio.sincronizar("MANUAL");

        siigoDevuelve(true, cotizacion("a", "C-1-1", 1, "2026-11-01", 1000, null));
        SincronizacionSiigo s = servicio.sincronizar("MANUAL");
        assertEquals(0, s.getEliminadas());
        assertEquals(2, servicio.buscar(null, null, null, false, 0, 25).totalElementos());
    }

    @Test
    void errorDeSiigo_quedaRegistradoComoFallida() {
        when(siigo.listarTodo(eq("quotations"), anyMap(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("Siigo caído"));
        assertThrows(RuntimeException.class, () -> servicio.sincronizar("MANUAL"));
        var ultima = sincronizaciones.findFirstByRecursoOrderByInicioDesc("quotations").orElseThrow();
        assertFalse(ultima.isExitosa());
        assertEquals("Siigo caído", ultima.getError());
    }

    @Test
    @WithMockUser(authorities = "cotizaciones:ver")
    void sinPermisoSiigo_403() throws Exception {
        mvc.perform(get("/api/siigo-cotizaciones")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "siigo:explorar")
    void conPermisoSiigo_listaDesdeLaBase() throws Exception {
        siigoDevuelve(false, cotizacion("a", "C-1-1", 1, "2026-11-01", 1000, null));
        servicio.sincronizar("MANUAL");
        mvc.perform(get("/api/siigo-cotizaciones").param("texto", "800197463"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].numero").value("C-1-1"))
                .andExpect(jsonPath("$.contenido[0].items").doesNotExist());
    }

    // ------------------------------------------------------------ utilidades

    private void siigoDevuelve(boolean incompleto, String... cotizacionesJson) throws Exception {
        String json = "{\"incompleto\":" + incompleto + ",\"results\":[" + String.join(",", cotizacionesJson) + "]}";
        JsonNode respuesta = mapper.readTree(json);
        when(siigo.listarTodo(eq("quotations"), anyMap(), anyBoolean(), anyBoolean())).thenReturn(respuesta);
    }

    private static String cotizacion(String id, String nombre, int numero, String fecha, int total,
                                     String actualizada) {
        return """
                {"id":"%s","document":{"id":32292},"number":%d,"name":"%s","date":"%s",
                 "customer":{"id":"cli-1","identification":"800197463","branch_office":0},
                 "seller":21669,"total":%d,
                 "items":[{"id":"prod-1","code":"61002","quantity":1,"price":%d,"description":"Mano de obra"}],
                 "metadata":{"created":"2026-11-01T09:00:00"%s},
                 "public_url":"https://publicview.siigo.com/x"}
                """.formatted(id, numero, nombre, fecha, total, total,
                actualizada == null ? "" : ",\"last_updated\":\"" + actualizada + "\"");
    }
}
