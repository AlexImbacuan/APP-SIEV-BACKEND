package com.ejemplo.demo.configuracion;

import com.ejemplo.demo.modelo.MigracionDatos;
import com.ejemplo.demo.modelo.Permiso;
import com.ejemplo.demo.modelo.Rol;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.MigracionDatosRepositorio;
import com.ejemplo.demo.repositorio.PermisoRepositorio;
import com.ejemplo.demo.repositorio.RolRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.Permisos;
import com.ejemplo.demo.servicio.AuditoriaServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Datos iniciales de RBAC. Se ejecuta en cada arranque y es idempotente:
 * solo crea lo que falta, nunca duplica.
 *
 * - Permisos: crea los del catálogo que no existan.
 * - Roles: crea SUPERADMIN, SUPERVISOR y TECNICO si no existen, con sus permisos
 *   por defecto. El rol SUPERADMIN siempre se sincroniza con todos los permisos.
 * - Permisos nuevos: cuando un permiso del catálogo se crea por PRIMERA vez, se
 *   agrega también a los roles existentes que lo tengan en su lista por defecto.
 *   Un permiso que ya existía nunca se vuelve a agregar, para no pisar los cambios
 *   que haga el SUPERADMIN.
 * - Superadmin: si ADMIN_EMAIL y ADMIN_PASSWORD están definidas, crea ese usuario
 *   (o le asigna SUPERADMIN si ya existía; su contraseña no se cambia).
 */
@Component
public class InicializadorDatos implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorDatos.class);

    private final PermisoRepositorio permisoRepositorio;
    private final RolRepositorio rolRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaServicio auditoria;
    private final MigracionDatosRepositorio migraciones;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    public InicializadorDatos(PermisoRepositorio permisoRepositorio,
                              RolRepositorio rolRepositorio,
                              UsuarioRepositorio usuarioRepositorio,
                              PasswordEncoder passwordEncoder,
                              AuditoriaServicio auditoria,
                              MigracionDatosRepositorio migraciones) {
        this.permisoRepositorio = permisoRepositorio;
        this.rolRepositorio = rolRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.migraciones = migraciones;
    }

    @Override
    @Transactional
    public void run(String... args) {

        Set<String> creadosAhora = new HashSet<>();
        Map<String, Permiso> permisos = crearPermisos(creadosAhora);

        Rol superadmin = crearRolSiNoExiste(Permisos.ROL_SUPERADMIN,
                "Acceso total; asigna roles y permisos", permisos.values());
        superadmin.getPermisos().addAll(permisos.values()); // siempre todos

        Rol supervisor = crearRolSiNoExiste(Permisos.ROL_SUPERVISOR, "Gestiona proyectos y empleados",
                seleccionar(permisos, Permisos.POR_DEFECTO_SUPERVISOR));
        agregarPermisosNuevos(supervisor, Permisos.POR_DEFECTO_SUPERVISOR, creadosAhora, permisos);
        supervisorSoloSiigo(supervisor, permisos);

        Rol tecnico = crearRolSiNoExiste(Permisos.ROL_TECNICO, "Consulta proyectos y empleados",
                seleccionar(permisos, Permisos.POR_DEFECTO_TECNICO));
        agregarPermisosNuevos(tecnico, Permisos.POR_DEFECTO_TECNICO, creadosAhora, permisos);

        crearSuperadmin(superadmin);
    }

    /** Crea los permisos que falten y anota en creadosAhora los códigos recién creados. */
    private Map<String, Permiso> crearPermisos(Set<String> creadosAhora) {

        Map<String, Permiso> resultado = new LinkedHashMap<>();

        for (Permisos.Definicion def : Permisos.CATALOGO) {
            Permiso permiso = permisoRepositorio.findByCodigo(def.codigo())
                    .orElseGet(() -> {
                        log.info("Creando permiso {}", def.codigo());
                        creadosAhora.add(def.codigo());
                        return permisoRepositorio.save(new Permiso(def.codigo(), def.descripcion(), def.modulo()));
                    });
            resultado.put(def.codigo(), permiso);
        }

        return resultado;
    }

    private Rol crearRolSiNoExiste(String nombre, String descripcion, Collection<Permiso> permisosPorDefecto) {

        return rolRepositorio.findByNombre(nombre).orElseGet(() -> {
            log.info("Creando rol {}", nombre);
            Rol rol = new Rol(nombre, descripcion);
            rol.getPermisos().addAll(permisosPorDefecto);
            rolRepositorio.save(rol);
            auditoria.registrarComo("sistema", "CREAR_ROL", AuditoriaServicio.ENTIDAD_ROL, rol.getId(),
                    "Seed inicial: " + nombre);
            return rol;
        });
    }

    /** Agrega al rol los permisos recién creados que estén en su lista por defecto. */
    private void agregarPermisosNuevos(Rol rol, List<String> porDefecto,
                                       Set<String> creadosAhora, Map<String, Permiso> permisos) {

        List<String> agregados = porDefecto.stream()
                .filter(creadosAhora::contains)
                .filter(codigo -> rol.getPermisos().add(permisos.get(codigo))) // false si ya lo tenía
                .toList();

        if (!agregados.isEmpty()) {
            log.info("Permisos nuevos agregados al rol {}: {}", rol.getNombre(), agregados);
            auditoria.registrarComo("sistema", "ACTUALIZAR_PERMISOS_ROL", AuditoriaServicio.ENTIDAD_ROL,
                    rol.getId(), "Seed: permisos nuevos por defecto " + agregados);
        }
    }

    /**
     * Migración única (2026-11): el SUPERVISOR deja el módulo viejo de cotizaciones
     * (a mano / PDF) y pasa a trabajar con las de Siigo. Se aplica también a un rol que
     * ya existe en la BD. Queda anotada en migracion_datos, así que no se repite y no
     * pisa lo que el SUPERADMIN cambie después desde la gestión de roles.
     */
    public static final String MIGRACION_SUPERVISOR_SIIGO = "2026-11-supervisor-solo-siigo";
    private static final Set<String> QUITAR_SUPERVISOR = Set.of(
            Permisos.COTIZACIONES_VER, Permisos.COTIZACIONES_CREAR, Permisos.COTIZACIONES_EDITAR,
            Permisos.COTIZACIONES_APROBAR, Permisos.COTIZACIONES_ELIMINAR);
    private static final List<String> AGREGAR_SUPERVISOR = List.of(Permisos.SIIGO_EXPLORAR, Permisos.SIIGO_EDITAR);

    private void supervisorSoloSiigo(Rol supervisor, Map<String, Permiso> permisos) {

        if (migraciones.existsById(MIGRACION_SUPERVISOR_SIIGO)) {
            return;
        }

        List<String> quitados = supervisor.getPermisos().stream()
                .map(Permiso::getCodigo).filter(QUITAR_SUPERVISOR::contains).sorted().toList();
        supervisor.getPermisos().removeIf(p -> QUITAR_SUPERVISOR.contains(p.getCodigo()));

        Set<String> actuales = new HashSet<>();
        supervisor.getPermisos().forEach(p -> actuales.add(p.getCodigo()));
        List<String> agregados = AGREGAR_SUPERVISOR.stream().filter(actuales::add).toList();
        agregados.forEach(codigo -> supervisor.getPermisos().add(permisos.get(codigo)));

        migraciones.save(new MigracionDatos(MIGRACION_SUPERVISOR_SIIGO));

        if (!quitados.isEmpty() || !agregados.isEmpty()) {
            log.info("Migración {}: SUPERVISOR quitados {} agregados {}", MIGRACION_SUPERVISOR_SIIGO, quitados, agregados);
            auditoria.registrarComo("sistema", "ACTUALIZAR_PERMISOS_ROL", AuditoriaServicio.ENTIDAD_ROL,
                    supervisor.getId(), "Migración " + MIGRACION_SUPERVISOR_SIIGO
                            + ": quitados " + quitados + ", agregados " + agregados);
        }
    }

    private void crearSuperadmin(Rol rolSuperadmin) {

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL y/o ADMIN_PASSWORD no están definidas: no se crea el usuario SUPERADMIN inicial");
            return;
        }

        String email = adminEmail.trim().toLowerCase();
        Usuario existente = usuarioRepositorio.findByEmail(email).orElse(null);

        if (existente == null) {
            Usuario admin = new Usuario();
            admin.setNombre("Super Administrador");
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRol(rolSuperadmin);
            admin.setActivo(true);
            usuarioRepositorio.save(admin);

            log.info("Usuario SUPERADMIN creado: {}", email);
            auditoria.registrarComo("sistema", "CREAR_USUARIO", AuditoriaServicio.ENTIDAD_USUARIO,
                    admin.getId(), "Seed inicial: SUPERADMIN " + email);

        } else if (!existente.tieneRol(Permisos.ROL_SUPERADMIN) || !existente.estaActivo()) {
            existente.setRol(rolSuperadmin);
            existente.setActivo(true);

            log.info("Usuario existente {} promovido a SUPERADMIN", email);
            auditoria.registrarComo("sistema", "ASIGNAR_ROL", AuditoriaServicio.ENTIDAD_USUARIO,
                    existente.getId(), "Seed inicial: " + email + " -> SUPERADMIN");
        }
    }

    private static List<Permiso> seleccionar(Map<String, Permiso> permisos, List<String> codigos) {
        return codigos.stream().map(permisos::get).toList();
    }
}
