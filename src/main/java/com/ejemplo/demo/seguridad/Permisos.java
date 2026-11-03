package com.ejemplo.demo.seguridad;

import java.util.List;

/**
 * Catálogo único de roles y permisos del sistema.
 * El seed (InicializadorDatos) crea en la BD todo lo que está aquí.
 * Para agregar un módulo nuevo: añade sus permisos a CATALOGO y protege
 * sus endpoints con @PreAuthorize("hasAuthority('modulo:accion')").
 */
public final class Permisos {

    private Permisos() {
    }

    // ---- Roles ----
    public static final String ROL_SUPERADMIN = "SUPERADMIN";
    public static final String ROL_SUPERVISOR = "SUPERVISOR";
    public static final String ROL_TECNICO = "TECNICO";

    // ---- Permisos ----
    public static final String PROYECTOS_VER = "proyectos:ver";
    public static final String PROYECTOS_CREAR = "proyectos:crear";
    public static final String PROYECTOS_EDITAR = "proyectos:editar";
    public static final String PROYECTOS_ELIMINAR = "proyectos:eliminar";

    public static final String EMPLEADOS_VER = "empleados:ver";
    public static final String EMPLEADOS_CREAR = "empleados:crear";
    public static final String EMPLEADOS_EDITAR = "empleados:editar";
    public static final String EMPLEADOS_ELIMINAR = "empleados:eliminar";

    public static final String USUARIOS_GESTIONAR = "usuarios:gestionar";
    public static final String ROLES_GESTIONAR = "roles:gestionar";

    public static final String AUDITORIA_VER = "auditoria:ver";

    public record Definicion(String codigo, String descripcion, String modulo) {
    }

    public static final List<Definicion> CATALOGO = List.of(
            new Definicion(PROYECTOS_VER, "Ver proyectos", "proyectos"),
            new Definicion(PROYECTOS_CREAR, "Crear proyectos", "proyectos"),
            new Definicion(PROYECTOS_EDITAR, "Editar proyectos", "proyectos"),
            new Definicion(PROYECTOS_ELIMINAR, "Eliminar proyectos", "proyectos"),

            new Definicion(EMPLEADOS_VER, "Ver empleados", "empleados"),
            new Definicion(EMPLEADOS_CREAR, "Crear empleados", "empleados"),
            new Definicion(EMPLEADOS_EDITAR, "Editar empleados", "empleados"),
            new Definicion(EMPLEADOS_ELIMINAR, "Eliminar empleados", "empleados"),

            new Definicion(USUARIOS_GESTIONAR, "Crear, editar, activar/desactivar usuarios y asignarles rol", "usuarios"),
            new Definicion(ROLES_GESTIONAR, "Crear roles y asignarles permisos", "roles"),

            new Definicion(AUDITORIA_VER, "Consultar el registro de auditoría", "auditoria")
    );

    /** Permisos por defecto de SUPERVISOR (solo se aplican al crear el rol). */
    public static final List<String> POR_DEFECTO_SUPERVISOR = List.of(
            PROYECTOS_VER, PROYECTOS_CREAR, PROYECTOS_EDITAR, PROYECTOS_ELIMINAR,
            EMPLEADOS_VER, EMPLEADOS_CREAR, EMPLEADOS_EDITAR, EMPLEADOS_ELIMINAR
    );

    /** Permisos por defecto de TECNICO (solo se aplican al crear el rol). */
    public static final List<String> POR_DEFECTO_TECNICO = List.of(
            PROYECTOS_VER, EMPLEADOS_VER
    );
}
