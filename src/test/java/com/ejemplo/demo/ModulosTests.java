package com.ejemplo.demo;

import com.ejemplo.demo.configuracion.InicializadorDatos;
import com.ejemplo.demo.modelo.Permiso;
import com.ejemplo.demo.modelo.Rol;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.MigracionDatosRepositorio;
import com.ejemplo.demo.repositorio.PermisoRepositorio;
import com.ejemplo.demo.repositorio.RolRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Acceso a los módulos administración / ventas / trabajador según el rol. */
@SpringBootTest
@AutoConfigureMockMvc
class ModulosTests {

    private static final String[] MODULOS = {"/api/administracion", "/api/ventas", "/api/trabajador"};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private RolRepositorio rolRepositorio;

    @Autowired
    private PermisoRepositorio permisoRepositorio;

    @Autowired
    private InicializadorDatos inicializadorDatos;

    @Autowired
    private MigracionDatosRepositorio migraciones;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void cadaRolVeSoloSusModulos() throws Exception {

        // ruta -> estado esperado para cada rol
        Map<String, Map<String, Integer>> esperado = Map.of(
                "SUPERADMIN", Map.of("/api/administracion", 200, "/api/ventas", 200, "/api/trabajador", 200),
                "SUPERVISOR", Map.of("/api/administracion", 403, "/api/ventas", 200, "/api/trabajador", 403),
                "TECNICO", Map.of("/api/administracion", 403, "/api/ventas", 403, "/api/trabajador", 200)
        );

        for (var rol : esperado.entrySet()) {
            String token = tokenDe("modulos-" + rol.getKey().toLowerCase() + "@test.com", rol.getKey());
            for (String ruta : MODULOS) {
                int estado = rol.getValue().get(ruta);
                mvc.perform(get(ruta).header("Authorization", token))
                        .andExpect(status().is(estado));
            }
        }
    }

    @Test
    void sinRol_noVeNingunModulo() throws Exception {
        String token = tokenDe("modulos-sinrol@test.com", null);
        for (String ruta : MODULOS) {
            mvc.perform(get(ruta).header("Authorization", token)).andExpect(status().isForbidden());
        }
    }

    @Test
    void sinToken_recibe401() throws Exception {
        for (String ruta : MODULOS) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void modulo_devuelveContenidoYUsuario() throws Exception {
        mvc.perform(get("/api/ventas").header("Authorization", tokenDe("vendedor@test.com", "SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulo").value("ventas"))
                .andExpect(jsonPath("$.titulo").value("Ventas"))
                .andExpect(jsonPath("$.usuario").value("vendedor@test.com"))
                .andExpect(jsonPath("$.rol").value("SUPERVISOR"));
    }

    @Test
    void me_incluyeLosPermisosDeModulos() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", tokenDe("modulos-tec2@test.com", "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos").value(org.hamcrest.Matchers.hasItem("trabajador:ver")))
                .andExpect(jsonPath("$.permisos").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("ventas:ver"))));
    }

    /**
     * Simula el caso real de Supabase: los roles ya existían cuando se agregó el permiso.
     * El seed debe dárselo al rol por defecto, pero no volver a dárselo si el admin lo quita.
     */
    @Test
    void seed_permisoNuevoLlegaARolExistente_peroRespetaCambiosDelAdmin() {
        try {
            // 1) Dejar la BD como antes de existir ventas:ver
            quitarPermisoDeTodosLosRoles("ventas:ver");
            permisoRepositorio.delete(permisoRepositorio.findByCodigo("ventas:ver").orElseThrow());
            assertFalse(rolTiene("SUPERVISOR", "ventas:ver"));

            // 2) Arranque con la versión nueva: crea el permiso y se lo da al SUPERVISOR existente
            inicializadorDatos.run();
            assertTrue(rolTiene("SUPERVISOR", "ventas:ver"));
            assertTrue(rolTiene("SUPERADMIN", "ventas:ver"));
            assertFalse(rolTiene("TECNICO", "ventas:ver"));

            // 3) El admin se lo quita al SUPERVISOR; un nuevo arranque NO debe devolvérselo
            quitarPermisoDe("SUPERVISOR", "ventas:ver");
            inicializadorDatos.run();
            assertFalse(rolTiene("SUPERVISOR", "ventas:ver"));

        } finally {
            // Restaurar para no afectar a otros tests que comparten la BD en memoria
            Rol supervisor = rolRepositorio.findByNombre("SUPERVISOR").orElseThrow();
            if (supervisor.getPermisos().stream().noneMatch(p -> p.getCodigo().equals("ventas:ver"))) {
                supervisor.getPermisos().add(permisoRepositorio.findByCodigo("ventas:ver").orElseThrow());
                rolRepositorio.save(supervisor);
            }
        }
    }

    /**
     * Caso real de Supabase: el SUPERVISOR ya existía con el módulo viejo de cotizaciones.
     * La migración le quita cotizaciones:* y le da siigo:*, conserva lo demás, es idempotente
     * y no se repite si después el admin cambia los permisos a mano.
     */
    @Test
    void migracion_supervisorSoloSiigo() {
        String[] viejos = {"cotizaciones:ver", "cotizaciones:crear", "cotizaciones:editar",
                "cotizaciones:aprobar", "cotizaciones:eliminar"};
        try {
            // 1) Dejar el SUPERVISOR como estaba en la BD antes del cambio
            Rol supervisor = rolRepositorio.findByNombre("SUPERVISOR").orElseThrow();
            supervisor.getPermisos().removeIf(p -> p.getCodigo().startsWith("siigo:"));
            for (String c : viejos) {
                supervisor.getPermisos().add(permisoRepositorio.findByCodigo(c).orElseThrow());
            }
            rolRepositorio.save(supervisor);
            migraciones.deleteById(InicializadorDatos.MIGRACION_SUPERVISOR_SIIGO);

            // 2) Arranque: se aplica
            inicializadorDatos.run();
            for (String c : viejos) {
                assertFalse(rolTiene("SUPERVISOR", c), c);
            }
            assertTrue(rolTiene("SUPERVISOR", "siigo:explorar"));
            assertTrue(rolTiene("SUPERVISOR", "siigo:editar"));
            assertTrue(rolTiene("SUPERVISOR", "proyectos:ver"), "conserva los demás");
            assertTrue(rolTiene("SUPERADMIN", "cotizaciones:ver"));
            assertTrue(rolTiene("SUPERADMIN", "siigo:editar"));
            assertTrue(rolTiene("SUPERADMIN", "clientes:gestionar"));
            assertFalse(rolTiene("SUPERVISOR", "clientes:gestionar"));
            int cantidad = rolRepositorio.findByNombre("SUPERVISOR").orElseThrow().getPermisos().size();

            // 3) Segundo arranque: no cambia nada
            inicializadorDatos.run();
            assertEquals(cantidad, rolRepositorio.findByNombre("SUPERVISOR").orElseThrow().getPermisos().size());

            // 4) El admin le devuelve cotizaciones:ver a mano; un nuevo arranque lo respeta
            Rol s = rolRepositorio.findByNombre("SUPERVISOR").orElseThrow();
            s.getPermisos().add(permisoRepositorio.findByCodigo("cotizaciones:ver").orElseThrow());
            rolRepositorio.save(s);
            inicializadorDatos.run();
            assertTrue(rolTiene("SUPERVISOR", "cotizaciones:ver"));
        } finally {
            quitarPermisoDe("SUPERVISOR", "cotizaciones:ver");
        }
    }

    // ------------------------------------------------------------ utilidades

    private boolean rolTiene(String rol, String codigo) {
        return rolRepositorio.findByNombre(rol).orElseThrow().getPermisos().stream()
                .map(Permiso::getCodigo)
                .anyMatch(codigo::equals);
    }

    private void quitarPermisoDe(String nombreRol, String codigo) {
        Rol rol = rolRepositorio.findByNombre(nombreRol).orElseThrow();
        rol.getPermisos().removeIf(p -> p.getCodigo().equals(codigo));
        rolRepositorio.save(rol);
    }

    private void quitarPermisoDeTodosLosRoles(String codigo) {
        for (Rol rol : rolRepositorio.findAll()) {
            if (rol.getPermisos().removeIf(p -> p.getCodigo().equals(codigo))) {
                rolRepositorio.save(rol);
            }
        }
    }

    private String tokenDe(String email, String nombreRol) {
        Usuario usuario = usuarioRepositorio.findByEmail(email).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre(email);
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode("x"));
            u.setActivo(true);
            u.setRol(nombreRol != null ? rolRepositorio.findByNombre(nombreRol).orElseThrow() : null);
            return usuarioRepositorio.save(u);
        });
        return "Bearer " + jwtService.generarToken(usuario);
    }
}
