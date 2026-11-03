package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.AuditoriaDTO;
import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.modelo.Auditoria;
import com.ejemplo.demo.repositorio.AuditoriaRepositorio;
import com.ejemplo.demo.seguridad.UsuarioActual;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AuditoriaServicio {

    public static final String ENTIDAD_USUARIO = "USUARIO";
    public static final String ENTIDAD_ROL = "ROL";

    /** Máximo de registros por página, para no cargar toda la tabla de golpe. */
    public static final int TAMANO_MAXIMO = 100;

    private final AuditoriaRepositorio auditoriaRepositorio;

    public AuditoriaServicio(AuditoriaRepositorio auditoriaRepositorio) {
        this.auditoriaRepositorio = auditoriaRepositorio;
    }

    /** Registra el cambio a nombre del usuario autenticado ("sistema" si no hay ninguno). */
    public void registrar(String accion, String entidad, Object entidadId, String detalle) {
        String actor = UsuarioActual.email();
        registrarComo(actor != null ? actor : "sistema", accion, entidad, entidadId, detalle);
    }

    public void registrarComo(String actor, String accion, String entidad, Object entidadId, String detalle) {
        auditoriaRepositorio.save(new Auditoria(
                actor,
                accion,
                entidad,
                entidadId != null ? entidadId.toString() : null,
                detalle
        ));
    }

    /**
     * Consulta paginada, de lo más reciente a lo más antiguo.
     * Todos los filtros son opcionales; solo se aplican los que vienen con valor.
     *
     * @param actor  coincidencia parcial, sin distinguir mayúsculas (ej. "admin")
     * @param desde  fecha inicial incluida
     * @param hasta  fecha final incluida (todo el día)
     */
    @Transactional(readOnly = true)
    public PaginaDTO<AuditoriaDTO> buscar(String entidad, String entidadId, String actor, String accion,
                                          LocalDate desde, LocalDate hasta, int pagina, int tamano) {

        Specification<Auditoria> filtro = (root, query, cb) -> cb.conjunction();

        if (tieneTexto(entidad)) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("entidad"), entidad.trim().toUpperCase()));
        }
        if (tieneTexto(entidadId)) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("entidadId"), entidadId.trim()));
        }
        if (tieneTexto(actor)) {
            String patron = "%" + actor.trim().toLowerCase() + "%";
            filtro = filtro.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("actor")), patron));
        }
        if (tieneTexto(accion)) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("accion"), accion.trim().toUpperCase()));
        }
        if (desde != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("fecha"), desde.atStartOfDay()));
        }
        if (hasta != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.lessThan(root.get("fecha"), hasta.plusDays(1).atStartOfDay()));
        }

        PageRequest paginacion = PageRequest.of(
                Math.max(pagina, 0),
                Math.min(Math.max(tamano, 1), TAMANO_MAXIMO),
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))
        );

        return PaginaDTO.desde(auditoriaRepositorio.findAll(filtro, paginacion), AuditoriaDTO::desde);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
