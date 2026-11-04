package com.ejemplo.demo;

import com.ejemplo.demo.configuracion.InicializadorDatos;
import com.ejemplo.demo.modelo.Rol;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.PermisoRepositorio;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de control de acceso. Usan H2 en memoria (src/test/resources/application.properties);
 * el seed crea permisos, roles y el SUPERADMIN admin@test.com al arrancar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadRbacTests {

    private static final String ADMIN = "admin@test.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private RolRepositorio rolRepositorio;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PermisoRepositorio permisoRepositorio;

    @Autowired
    private InicializadorDatos inicializadorDatos;

    // ------------------------------------------------------------ 401

    @Test
    void sinToken_recibe401EnJson() throws Exception {
        mvc.perform(get("/api/proyectos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.ruta").value("/api/proyectos"));
    }

    @Test
    void tokenInvalido_recibe401() throws Exception {
        mvc.perform(get("/api/proyectos").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginConPasswordIncorrecta_recibe401() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN + "\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401));
    }

    @Test
    void loginCorrecto_devuelveToken() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN + "\",\"password\":\"Admin-Test-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ------------------------------------------------------------ 403 / 200

    @Test
    void tecnico_conPermiso_recibe200() throws Exception {
        String token = tokenDe("tecnico1@test.com", "TECNICO");

        mvc.perform(get("/api/proyectos").header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/empleados").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void tecnico_sinPermiso_recibe403EnJson() throws Exception {
        String token = tokenDe("tecnico2@test.com", "TECNICO");

        mvc.perform(post("/api/proyectos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proyectoJson("T-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));

        mvc.perform(get("/api/usuarios").header("Authorization", token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/roles").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioSinRol_recibe403() throws Exception {
        String token = tokenDe("sinrol@test.com", null);

        mvc.perform(get("/api/proyectos").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void superadmin_accedeATodo() throws Exception {
        String token = tokenDeAdmin();

        for (String ruta : new String[]{"/api/proyectos", "/api/empleados", "/api/usuarios",
                "/api/roles", "/api/permisos", "/api/auditoria", "/auth/me", "/api/auth/me"}) {
            mvc.perform(get(ruta).header("Authorization", token))
                    .andExpect(status().isOk());
        }

        mvc.perform(post("/api/proyectos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proyectoJson("SA-1")))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ /me

    @Test
    void me_devuelveRolYPermisosSinPassword() throws Exception {
        String token = tokenDe("tecnico3@test.com", "TECNICO");

        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tecnico3@test.com"))
                .andExpect(jsonPath("$.rol").value("TECNICO"))
                .andExpect(jsonPath("$.permisos", hasItems("proyectos:ver", "empleados:ver")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ------------------------------------------------------------ cambios inmediatos

    @Test
    void cambioDePermisos_seAplicaSinNuevoToken() throws Exception {
        String admin = tokenDeAdmin();

        // El SUPERADMIN crea un rol sin permisos
        String rolJson = mvc.perform(post("/api/roles").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"rol_prueba\",\"descripcion\":\"test\",\"permisos\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("ROL_PRUEBA"))
                .andReturn().getResponse().getContentAsString();
        long rolId = objectMapper.readTree(rolJson).get("id").asLong();

        String usuario = tokenDe("cambiante@test.com", "ROL_PRUEBA");

        mvc.perform(get("/api/proyectos").header("Authorization", usuario))
                .andExpect(status().isForbidden());

        // Se le da proyectos:ver al rol...
        mvc.perform(put("/api/roles/" + rolId + "/permisos").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permisos\":[\"proyectos:ver\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos", hasItem("proyectos:ver")));

        // ...y el MISMO token ya tiene acceso
        mvc.perform(get("/api/proyectos").header("Authorization", usuario))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioDesactivado_pierdeAccesoAlInstante() throws Exception {
        String admin = tokenDeAdmin();
        String token = tokenDe("desactivar@test.com", "TECNICO");
        Long id = usuarioRepositorio.findByEmail("desactivar@test.com").orElseThrow().getId();

        mvc.perform(get("/api/proyectos").header("Authorization", token))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/usuarios/" + id + "/activo").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mvc.perform(get("/api/proyectos").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ reglas SUPERADMIN

    @Test
    void superadmin_noPuedeQuitarseSuPropioRol() throws Exception {
        Long adminId = usuarioRepositorio.findByEmail(ADMIN).orElseThrow().getId();
        Long tecnicoId = rolRepositorio.findByNombre("TECNICO").orElseThrow().getId();

        mvc.perform(put("/api/usuarios/" + adminId + "/rol").header("Authorization", tokenDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolId\":" + tecnicoId + "}"))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/usuarios/" + adminId + "/activo").header("Authorization", tokenDeAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void crearUsuario_noDevuelvePassword() throws Exception {
        Long tecnicoId = rolRepositorio.findByNombre("TECNICO").orElseThrow().getId();

        JsonNode creado = objectMapper.readTree(
                mvc.perform(post("/api/usuarios").header("Authorization", tokenDeAdmin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nombre\":\"Nuevo\",\"email\":\"nuevo@test.com\",\"password\":\"Clave-123\",\"rolId\":" + tecnicoId + "}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.rol").value("TECNICO"))
                        .andExpect(jsonPath("$.password").doesNotExist())
                        .andReturn().getResponse().getContentAsString());

        // La contraseña quedó cifrada con BCrypt
        Usuario guardado = usuarioRepositorio.findById(creado.get("id").asLong()).orElseThrow();
        assertTrue(guardado.getPassword().startsWith("$2"), "La contraseña debe estar cifrada con BCrypt");
    }

    // ------------------------------------------------------------ seed

    @Test
    void seed_esIdempotente() {
        long permisos = permisoRepositorio.count();
        long roles = rolRepositorio.count();
        long usuarios = usuarioRepositorio.count();

        // Ejecutarlo de nuevo (como en un reinicio) no debe duplicar nada
        inicializadorDatos.run();
        inicializadorDatos.run();

        assertEquals(permisos, permisoRepositorio.count());
        assertEquals(roles, rolRepositorio.count());
        assertEquals(usuarios, usuarioRepositorio.count());
        assertEquals(com.ejemplo.demo.seguridad.Permisos.CATALOGO.size(), permisos);
    }

    // ------------------------------------------------------------ auditoría

    @Test
    void auditoria_sinPermiso_recibe403() throws Exception {
        mvc.perform(get("/api/auditoria").header("Authorization", tokenDe("tecnico4@test.com", "TECNICO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditoria_registraQuienCambioQue() throws Exception {
        String admin = tokenDeAdmin();
        Long tecnicoId = rolRepositorio.findByNombre("TECNICO").orElseThrow().getId();

        String creado = mvc.perform(post("/api/usuarios").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Auditado\",\"email\":\"auditado@test.com\",\"password\":\"Clave-123\",\"rolId\":" + tecnicoId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(creado).get("id").asText();

        // Filtrando por ese usuario aparece el cambio, hecho por el admin
        mvc.perform(get("/api/auditoria")
                        .param("entidad", "usuario") // no distingue mayúsculas
                        .param("entidadId", id)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].accion").value("CREAR_USUARIO"))
                .andExpect(jsonPath("$.contenido[0].actor").value(ADMIN))
                .andExpect(jsonPath("$.contenido[0].detalle").value("email=auditado@test.com, rol=TECNICO"));
    }

    @Test
    void auditoria_paginaYLimitaElTamano() throws Exception {
        mvc.perform(get("/api/auditoria")
                        .param("tamano", "5000")
                        .header("Authorization", tokenDeAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamano").value(100))
                .andExpect(jsonPath("$.pagina").value(0));
    }

    @Test
    void auditoria_fechaInvalida_recibe400EnJson() throws Exception {
        mvc.perform(get("/api/auditoria")
                        .param("desde", "ayer")
                        .header("Authorization", tokenDeAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));
    }

    // ------------------------------------------------------------ roles

    @Test
    void gestorDeUsuarios_puedeListarRolesPeroNoModificarlos() throws Exception {
        Rol gestor = rolRepositorio.findByNombre("GESTOR_USUARIOS_TEST").orElseGet(() -> {
            Rol r = new Rol("GESTOR_USUARIOS_TEST", "Solo gestiona usuarios");
            r.getPermisos().add(permisoRepositorio.findByCodigo("usuarios:gestionar").orElseThrow());
            return rolRepositorio.save(r);
        });
        String token = tokenDe("gestor-usuarios@test.com", gestor.getNombre());

        // Puede leer la lista de roles (la necesita para el selector)
        mvc.perform(get("/api/roles").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItems("SUPERADMIN", "SUPERVISOR", "TECNICO")));

        // Pero no crear roles, cambiar sus permisos ni ver el catálogo de permisos
        mvc.perform(post("/api/roles").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"NO_DEBE_CREARSE\",\"descripcion\":\"x\",\"permisos\":[]}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/roles/" + gestor.getId() + "/permisos").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permisos\":[\"roles:gestionar\"]}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/permisos").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinGestionarUsuariosNiRoles_noPuedeListarRoles() throws Exception {
        mvc.perform(get("/api/roles").header("Authorization", tokenDe("tecnico1@test.com", "TECNICO")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ utilidades

    private String tokenDeAdmin() {
        Usuario admin = usuarioRepositorio.findByEmail(ADMIN).orElseThrow();
        return "Bearer " + jwtService.generarToken(admin);
    }

    /** Crea (si no existe) un usuario activo con el rol indicado y devuelve su header Authorization. */
    private String tokenDe(String email, String nombreRol) {
        Usuario usuario = usuarioRepositorio.findByEmail(email).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre(email);
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode("x"));
            u.setActivo(true);
            Rol rol = nombreRol != null ? rolRepositorio.findByNombre(nombreRol).orElseThrow() : null;
            u.setRol(rol);
            return usuarioRepositorio.save(u);
        });
        return "Bearer " + jwtService.generarToken(usuario);
    }

    private static String proyectoJson(String serial) {
        return "{\"serial\":\"" + serial + "\",\"descripcionGlobal\":\"Prueba\",\"valorTotal\":1000,"
                + "\"factura\":1,\"pago\":false,\"observacion\":\"test\"}";
    }
}
