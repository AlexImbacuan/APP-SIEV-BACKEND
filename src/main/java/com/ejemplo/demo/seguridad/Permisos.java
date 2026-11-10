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

    public static final String COTIZACIONES_VER = "cotizaciones:ver";
    public static final String COTIZACIONES_CREAR = "cotizaciones:crear";
    public static final String COTIZACIONES_EDITAR = "cotizaciones:editar";
    public static final String COTIZACIONES_APROBAR = "cotizaciones:aprobar";
    public static final String COTIZACIONES_ELIMINAR = "cotizaciones:eliminar";

    /** Temporal: consulta directa a la API de Siigo. Solo SUPERADMIN (no está en ningún rol por defecto). */
    public static final String SIIGO_EXPLORAR = "siigo:explorar";

    // ---- Módulos del frontend ----
    public static final String ADMINISTRACION_VER = "administracion:ver";
    public static final String VENTAS_VER = "ventas:ver";
    public static final String TRABAJADOR_VER = "trabajador:ver";

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

            new Definicion(AUDITORIA_VER, "Consultar el registro de auditoría", "auditoria"),

            new Definicion(COTIZACIONES_VER, "Ver cotizaciones", "cotizaciones"),
            new Definicion(COTIZACIONES_CREAR, "Crear cotizaciones (a mano o desde el PDF de Siigo)", "cotizaciones"),
            new Definicion(COTIZACIONES_EDITAR, "Editar cotizaciones, materiales, notas y número de factura", "cotizaciones"),
            new Definicion(COTIZACIONES_APROBAR, "Cambiar el estado de las cotizaciones", "cotizaciones"),
            new Definicion(COTIZACIONES_ELIMINAR, "Eliminar cotizaciones", "cotizaciones"),

            new Definicion(SIIGO_EXPLORAR, "Consultar datos directamente de la API de Siigo (temporal)", "siigo"),

            new Definicion(ADMINISTRACION_VER, "Ver el módulo de administración", "administracion"),
            new Definicion(VENTAS_VER, "Ver el módulo de ventas", "ventas"),
            new Definicion(TRABAJADOR_VER, "Ver el módulo de trabajador", "trabajador")
    );

    /*
     * Permisos por defecto de cada rol. Se aplican:
     *  - al CREAR el rol (todos los de la lista), y
     *  - al CREAR un permiso nuevo del catálogo (solo ese permiso, a los roles que lo
     *    tengan en su lista). Así un permiso agregado después llega a los roles que ya
     *    existen en la BD, pero si el SUPERADMIN se lo quita luego, no vuelve a aparecer.
     * El SUPERADMIN no necesita lista: siempre tiene todos.
     */

    public static final List<String> POR_DEFECTO_SUPERVISOR = List.of(
            PROYECTOS_VER, PROYECTOS_CREAR, PROYECTOS_EDITAR, PROYECTOS_ELIMINAR,
            EMPLEADOS_VER, EMPLEADOS_CREAR, EMPLEADOS_EDITAR, EMPLEADOS_ELIMINAR,
            VENTAS_VER,
            COTIZACIONES_VER, COTIZACIONES_CREAR, COTIZACIONES_EDITAR, COTIZACIONES_APROBAR,
            COTIZACIONES_ELIMINAR
    );

    public static final List<String> POR_DEFECTO_TECNICO = List.of(
            PROYECTOS_VER, EMPLEADOS_VER,
            TRABAJADOR_VER,
            COTIZACIONES_VER
    );
}
