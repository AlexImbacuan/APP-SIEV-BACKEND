package com.ejemplo.demo;

import com.ejemplo.demo.modelo.Rol;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.CotizacionRepositorio;
import com.ejemplo.demo.repositorio.RolRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Módulo de cotizaciones: CRUD, estados, permisos y lectura del PDF de Siigo. */
@SpringBootTest
@AutoConfigureMockMvc
class CotizacionesTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private RolRepositorio rolRepositorio;

    @Autowired
    private CotizacionRepositorio cotizacionRepositorio;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------ crear

    @Test
    void crearAMano_calculaValoresYQuedaPendiente() throws Exception {
        String gestor = tokenDe("cot-gestor@test.com", "SUPERADMIN");
        long responsableId = usuario("cot-gestor@test.com").getId();
        String numero = numeroNuevo();

        // Sin subtotal/iva/total ni valorBruto: se calculan
        String json = """
                {"numero":"%s","cliente":"Cliente Uno","ingSolicitante":"Ing. Prueba",
                 "responsableId":%d,
                 "items":[{"descripcion":"Cable","cantidad":10,"valorUnitario":1000},
                          {"descripcion":"Mano de obra","cantidad":1,"valorUnitario":90000}],
                 "notas":"Primera nota"}
                """.formatted(numero, responsableId);

        mvc.perform(post("/api/cotizaciones").header("Authorization", gestor)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(numero))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.responsable.id").value(responsableId))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].valorBruto").value(10000.0))
                .andExpect(jsonPath("$.subtotal").value(100000.0))
                .andExpect(jsonPath("$.iva").value(19000.0))
                .andExpect(jsonPath("$.total").value(119000.0))
                .andExpect(jsonPath("$.facturada").value(false))
                .andExpect(jsonPath("$.avisos", hasSize(0)));
    }

    @Test
    void numeroRepetido_recibe409() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        String numero = numeroNuevo();

        crear(admin, numero).andExpect(status().isCreated());
        crear(admin, numero.toLowerCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString(numero.toLowerCase())));
    }

    @Test
    void camposObligatorios_recibe400() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");

        mvc.perform(post("/api/cotizaciones").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cliente\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El campo 'numero' es obligatorio"));

        mvc.perform(post("/api/cotizaciones").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"" + numeroNuevo() + "\",\"cliente\":\"X\","
                                + "\"items\":[{\"descripcion\":\"Cable\",\"valorUnitario\":5}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El campo 'items[1].cantidad' es obligatorio"));
    }

    @Test
    void valoresQueNoCuadran_seGuardanConAvisos() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");

        String json = """
                {"numero":"%s","cliente":"Cliente","items":[
                   {"descripcion":"Cable","cantidad":2,"valorUnitario":1000,"valorBruto":5000}],
                 "subtotal":9000,"iva":100,"total":1}
                """.formatted(numeroNuevo());

        mvc.perform(post("/api/cotizaciones").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos", hasSize(4)));
    }

    @Test
    void responsableInvalido_recibe400() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        Usuario sinRol = usuarioCon("cot-sinrol@test.com", null);

        mvc.perform(post("/api/cotizaciones").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"" + numeroNuevo() + "\",\"cliente\":\"X\",\"responsableId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El responsable indicado no existe"));

        mvc.perform(post("/api/cotizaciones").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"" + numeroNuevo() + "\",\"cliente\":\"X\",\"responsableId\":"
                                + sinRol.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ estado, edición, filtros

    @Test
    void cambiarEstado_noAprobadaExigeMotivo() throws Exception {
        String gestor = tokenDe("cot-gestor@test.com", "SUPERADMIN");
        long id = idDe(crear(gestor, numeroNuevo()));

        mvc.perform(patch("/api/cotizaciones/" + id + "/estado").header("Authorization", gestor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"NO_APROBADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El campo 'motivoRechazo' es obligatorio"));

        mvc.perform(patch("/api/cotizaciones/" + id + "/estado").header("Authorization", gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"NO_APROBADA\",\"motivoRechazo\":\"Precio alto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_APROBADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("Precio alto"));

        // Al aprobarla se borra el motivo
        mvc.perform(patch("/api/cotizaciones/" + id + "/estado").header("Authorization", gestor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"APROBADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.motivoRechazo").doesNotExist());
    }

    @Test
    void estadoInexistente_recibe400EnJson() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        long id = idDe(crear(admin, numeroNuevo()));

        mvc.perform(patch("/api/cotizaciones/" + id + "/estado").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"QUIEN_SABE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));
    }

    @Test
    void editarConNumeroDeFactura_quedaFacturadaYSeFiltra() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        String numero = numeroNuevo();
        long id = idDe(crear(admin, numero));

        String json = """
                {"numero":"%s","cliente":"Cliente Editado","numeroFactura":"FV-001-933",
                 "items":[{"descripcion":"Breaker","cantidad":1,"valorUnitario":35000}]}
                """.formatted(numero);

        mvc.perform(put("/api/cotizaciones/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente").value("Cliente Editado"))
                .andExpect(jsonPath("$.numeroFactura").value("FV-001-933"))
                .andExpect(jsonPath("$.facturada").value(true))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        mvc.perform(get("/api/cotizaciones").param("facturada", "true").param("texto", numero)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(id))
                .andExpect(jsonPath("$.contenido[0].facturada").value(true));

        mvc.perform(get("/api/cotizaciones").param("facturada", "false").param("texto", numero)
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void eliminar_luegoNoExiste() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        long id = idDe(crear(admin, numeroNuevo()));

        mvc.perform(delete("/api/cotizaciones/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cotizaciones/" + id).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ permisos

    @Test
    void tecnico_soloPuedeVer() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        String tecnico = tokenDe("cot-tecnico@test.com", "TECNICO");
        long id = idDe(crear(admin, numeroNuevo()));

        mvc.perform(get("/api/cotizaciones").header("Authorization", tecnico)).andExpect(status().isOk());
        mvc.perform(get("/api/cotizaciones/" + id).header("Authorization", tecnico)).andExpect(status().isOk());
        mvc.perform(get("/api/cotizaciones/responsables").header("Authorization", tecnico))
                .andExpect(status().isOk());

        crear(tecnico, numeroNuevo()).andExpect(status().isForbidden());
        mvc.perform(patch("/api/cotizaciones/" + id + "/estado").header("Authorization", tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"APROBADA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/cotizaciones/" + id).header("Authorization", tecnico))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/cotizaciones/leer-pdf").file(pdf(PdfCotizacionDePrueba.generar()))
                        .header("Authorization", tecnico))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinRol_noVeCotizaciones() throws Exception {
        mvc.perform(get("/api/cotizaciones").header("Authorization", tokenDe("cot-sinrol@test.com", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsables_soloActivosConRol() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        usuarioCon("cot-sinrol@test.com", null);
        Usuario inactivo = usuarioCon("cot-inactivo@test.com", "TECNICO");
        inactivo.setActivo(false);
        usuarioRepositorio.save(inactivo);

        mvc.perform(get("/api/cotizaciones/responsables").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem("cot-admin@test.com")))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("cot-sinrol@test.com"))))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("cot-inactivo@test.com"))))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    // ------------------------------------------------------------ PDF

    @Test
    void leerPdf_devuelveDatosYNoGuardaNada() throws Exception {
        String gestor = tokenDe("cot-gestor@test.com", "SUPERADMIN");
        cotizacionRepositorio.findByNumeroIgnoreCase("C-1-9001").ifPresent(cotizacionRepositorio::delete);
        long antes = cotizacionRepositorio.count();

        mvc.perform(multipart("/api/cotizaciones/leer-pdf").file(pdf(PdfCotizacionDePrueba.generar()))
                        .header("Authorization", gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("C-1-9001"))
                .andExpect(jsonPath("$.cliente").value("CLIENTE DE PRUEBA S.A"))
                .andExpect(jsonPath("$.ingSolicitante").value("ANA PEREZ"))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[1].descripcion").value("Tubería IMC 3/4"))
                .andExpect(jsonPath("$.subtotal").value(2000000.0))
                .andExpect(jsonPath("$.iva").value(380000.0))
                .andExpect(jsonPath("$.total").value(2380000.0))
                .andExpect(jsonPath("$.avisos", hasSize(0)))
                .andExpect(jsonPath("$.idExistente").doesNotExist());

        assertEquals(antes, cotizacionRepositorio.count());
    }

    @Test
    void leerPdf_numeroYaExistente_loAvisa() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");
        cotizacionRepositorio.findByNumeroIgnoreCase("C-1-9001").ifPresent(cotizacionRepositorio::delete);
        long id = idDe(crear(admin, "C-1-9001"));

        mvc.perform(multipart("/api/cotizaciones/leer-pdf").file(pdf(PdfCotizacionDePrueba.generar()))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idExistente").value(id))
                .andExpect(jsonPath("$.avisos", hasItem("Ya existe una cotización con el número C-1-9001")));
    }

    @Test
    void leerPdf_archivoInvalido_recibe400EnJson() throws Exception {
        String admin = tokenDe("cot-admin@test.com", "SUPERADMIN");

        mvc.perform(multipart("/api/cotizaciones/leer-pdf")
                        .file(new MockMultipartFile("archivo", "foto.pdf", "application/pdf", "no soy pdf".getBytes()))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El archivo no es un PDF válido"));

        mvc.perform(multipart("/api/cotizaciones/leer-pdf")
                        .file(new MockMultipartFile("otroCampo", "c.pdf", "application/pdf",
                                PdfCotizacionDePrueba.generar()))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el campo 'archivo'"));
    }

    // ------------------------------------------------------------ utilidades

    private ResultActions crear(String token, String numero) throws Exception {
        return mvc.perform(post("/api/cotizaciones").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"" + numero + "\",\"cliente\":\"Cliente\","
                        + "\"items\":[{\"descripcion\":\"Cable\",\"cantidad\":1,\"valorUnitario\":1000}]}"));
    }

    private long idDe(ResultActions resultado) throws Exception {
        JsonNode json = objectMapper.readTree(resultado.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private static MockMultipartFile pdf(byte[] contenido) {
        return new MockMultipartFile("archivo", "cotizacion.pdf", "application/pdf", contenido);
    }

    private static String numeroNuevo() {
        return "C-T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Usuario usuario(String email) {
        return usuarioRepositorio.findByEmail(email).orElseThrow();
    }

    /** Crea (si no existe) un usuario activo con el rol indicado. */
    private Usuario usuarioCon(String email, String nombreRol) {
        return usuarioRepositorio.findByEmail(email).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre(email);
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode("x"));
            u.setActivo(true);
            Rol rol = nombreRol != null ? rolRepositorio.findByNombre(nombreRol).orElseThrow() : null;
            u.setRol(rol);
            return usuarioRepositorio.save(u);
        });
    }

    private String tokenDe(String email, String nombreRol) {
        return "Bearer " + jwtService.generarToken(usuarioCon(email, nombreRol));
    }
}
