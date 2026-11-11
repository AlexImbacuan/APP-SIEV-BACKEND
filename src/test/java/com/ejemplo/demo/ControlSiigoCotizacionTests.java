package com.ejemplo.demo;

import com.ejemplo.demo.modelo.Rol;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.ClienteRepositorio;
import com.ejemplo.demo.repositorio.RolRepositorio;
import com.ejemplo.demo.repositorio.SiigoCotizacionRepositorio;
import com.ejemplo.demo.repositorio.SincronizacionSiigoRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.JwtService;
import com.ejemplo.demo.servicio.SiigoCliente;
import com.ejemplo.demo.servicio.SiigoSincronizacionServicio;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Control SIEV sobre las cotizaciones de Siigo y catálogo de clientes. */
@SpringBootTest
@AutoConfigureMockMvc
class ControlSiigoCotizacionTests {

    @MockitoBean
    private SiigoCliente siigo;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private SiigoSincronizacionServicio sincronizacion;
    @Autowired private SiigoCotizacionRepositorio cotizaciones;
    @Autowired private SincronizacionSiigoRepositorio sincronizaciones;
    @Autowired private ClienteRepositorio clientes;
    @Autowired private UsuarioRepositorio usuarios;
    @Autowired private RolRepositorio roles;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder encoder;

    private String admin;
    private String supervisor;
    private long idCotizacion;

    @BeforeEach
    void preparar() throws Exception {
        cotizaciones.deleteAll();
        sincronizaciones.deleteAll();
        clientes.deleteAll();
        admin = tokenDe("ctrl-admin@test.com", "SUPERADMIN");
        supervisor = tokenDe("ctrl-supervisor@test.com", "SUPERVISOR");

        String json = """
                {"incompleto":false,"results":[{
                  "id":"q-1","number":2856,"name":"C-1-2856","date":"2026-11-10",
                  "customer":{"id":"cli","identification":"800197463"},"seller":21669,"total":2677500,
                  "observations":"Señor\\nJuan Carlos Soler\\nCargill – Planta Villagorgona\\nAsunto: Cotización_2856_Suministro de personal",
                  "items":[
                    {"id":"p1","code":"24011","quantity":9,"price":250000,"description":"Técnico",
                     "discount":{"percentage":0,"value":0},
                     "taxes":[{"id":1,"name":"IVA 19%","type":"IVA","percentage":19,"value":427500}],"total":2677500}
                  ],
                  "metadata":{"created":"2026-11-10T11:31:23"}}]}
                """;
        when(siigo.listarTodo(eq("quotations"), anyMap(), anyBoolean(), anyBoolean())).thenReturn(mapper.readTree(json));
        sincronizacion.sincronizar("MANUAL");
        idCotizacion = cotizaciones.findAll().get(0).getId();
    }

    @Test
    void sincronizacion_calculaSubtotalIvaYLeeElEncabezado() throws Exception {
        mvc.perform(get("/api/siigo-cotizaciones/" + idCotizacion).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(2250000))
                .andExpect(jsonPath("$.iva").value(427500))
                .andExpect(jsonPath("$.total").value(2677500))
                .andExpect(jsonPath("$.descripcion").value("Suministro de personal"))
                .andExpect(jsonPath("$.ingenieroSolicitante").value("Juan Carlos Soler"))
                .andExpect(jsonPath("$.sede").value("Cargill – Planta Villagorgona"))
                .andExpect(jsonPath("$.estado").value("POR_APROBAR"));
    }

    @Test
    void empresa_saleDelCatalogoDeClientes() throws Exception {
        mvc.perform(get("/api/siigo-cotizaciones").header("Authorization", admin))
                .andExpect(jsonPath("$.contenido[0].empresa").doesNotExist());

        mvc.perform(post("/api/clientes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":\"800.197.463-1\",\"nombre\":\"Cargill de Colombia\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nit").value("800197463"))
                .andExpect(jsonPath("$.cotizaciones").value(1));

        mvc.perform(get("/api/siigo-cotizaciones").header("Authorization", admin))
                .andExpect(jsonPath("$.contenido[0].empresa").value("Cargill de Colombia"));
        // Se puede buscar por el nombre de la empresa
        mvc.perform(get("/api/siigo-cotizaciones").param("texto", "cargill").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElementos").value(1));

        // NIT repetido -> 409
        mvc.perform(post("/api/clientes").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":\"800197463\",\"nombre\":\"Otro\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nitsSinCliente() throws Exception {
        mvc.perform(get("/api/clientes/nits-sin-cliente").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nit").value("800197463"))
                .andExpect(jsonPath("$[0].cotizaciones").value(1));
    }

    @Test
    void control_seGuardaYNoLoPisaLaSincronizacion() throws Exception {
        long responsableId = usuarios.findByEmail("ctrl-admin@test.com").orElseThrow().getId();
        String control = """
                {"descripcion":"Reparación lámparas bodega 3","ingenieroSolicitante":null,
                 "responsableId":%d,"ordenCompra":"OC-4500123","valorOc":2677500,
                 "numeroFactura":"FV-1020","fechaFactura":"2026-11-20","fechaPago":"2026-12-15",
                 "comprobantePago":"RC-555","valorPagado":2677500}
                """.formatted(responsableId);
        mvc.perform(put("/api/siigo-cotizaciones/" + idCotizacion + "/control").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(control))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Reparación lámparas bodega 3"))
                .andExpect(jsonPath("$.descripcionSiigo").value("Suministro de personal"))
                .andExpect(jsonPath("$.ingenieroSolicitante").value("Juan Carlos Soler")) // null -> el de Siigo
                .andExpect(jsonPath("$.responsable.id").value(responsableId))
                .andExpect(jsonPath("$.ordenCompra").value("OC-4500123"))
                .andExpect(jsonPath("$.fechaPago").value("2026-12-15"))
                .andExpect(jsonPath("$.controlActualizadoPor").value("ctrl-admin@test.com"));

        mvc.perform(patch("/api/siigo-cotizaciones/" + idCotizacion + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"POR_FACTURAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("POR_FACTURAR"));

        sincronizacion.sincronizar("MANUAL"); // la sincronización no toca el control

        mvc.perform(get("/api/siigo-cotizaciones/" + idCotizacion).header("Authorization", admin))
                .andExpect(jsonPath("$.estado").value("POR_FACTURAR"))
                .andExpect(jsonPath("$.numeroFactura").value("FV-1020"))
                .andExpect(jsonPath("$.descripcion").value("Reparación lámparas bodega 3"));

        mvc.perform(get("/api/siigo-cotizaciones").param("estado", "POR_FACTURAR").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/siigo-cotizaciones").param("estado", "POR_APROBAR").header("Authorization", admin))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void validaciones() throws Exception {
        mvc.perform(put("/api/siigo-cotizaciones/" + idCotizacion + "/control").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valorOc\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/siigo-cotizaciones/" + idCotizacion + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"INVENTADO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/siigo-cotizaciones/" + idCotizacion + "/control").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"responsableId\":999999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supervisor_veYEditaCotizacionesPeroNoGestionaClientes() throws Exception {
        mvc.perform(get("/api/siigo-cotizaciones").header("Authorization", supervisor))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/siigo-cotizaciones/" + idCotizacion + "/estado").header("Authorization", supervisor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PAGADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));
        mvc.perform(get("/api/clientes").header("Authorization", supervisor))
                .andExpect(status().isOk());
        mvc.perform(post("/api/clientes").header("Authorization", supervisor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":\"1\",\"nombre\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ utilidades

    private String tokenDe(String email, String nombreRol) {
        Usuario u = usuarios.findByEmail(email).orElseGet(() -> {
            Usuario n = new Usuario();
            n.setNombre(email);
            n.setEmail(email);
            n.setPassword(encoder.encode("x"));
            n.setActivo(true);
            Rol rol = roles.findByNombre(nombreRol).orElseThrow();
            n.setRol(rol);
            return usuarios.save(n);
        });
        return "Bearer " + jwt.generarToken(u);
    }
}
