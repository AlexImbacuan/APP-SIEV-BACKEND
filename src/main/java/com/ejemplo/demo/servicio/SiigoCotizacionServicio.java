package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.ControlSiigoCotizacionRequest;
import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.dto.SiigoCotizacionDTO;
import com.ejemplo.demo.modelo.Cliente;
import com.ejemplo.demo.modelo.EstadoSiigoCotizacion;
import com.ejemplo.demo.modelo.SiigoCotizacion;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.ClienteRepositorio;
import com.ejemplo.demo.repositorio.SiigoCotizacionRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Consulta y control SIEV de las cotizaciones copiadas de Siigo. */
@Service
public class SiigoCotizacionServicio {

    static final String ENTIDAD = "SIIGO_COTIZACION";

    private final SiigoCotizacionRepositorio cotizaciones;
    private final ClienteRepositorio clientes;
    private final UsuarioRepositorio usuarios;
    private final AuditoriaServicio auditoria;
    private final ObjectMapper mapper;

    public SiigoCotizacionServicio(SiigoCotizacionRepositorio cotizaciones, ClienteRepositorio clientes,
                                   UsuarioRepositorio usuarios, AuditoriaServicio auditoria, ObjectMapper mapper) {
        this.cotizaciones = cotizaciones;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ consultas

    /**
     * texto busca en: número, NIT, nombre de la empresa (catálogo de clientes), descripción,
     * ingeniero, orden de compra y factura.
     */
    @Transactional(readOnly = true)
    public PaginaDTO<SiigoCotizacionDTO> buscar(String texto, EstadoSiigoCotizacion estado, Long responsableId,
                                                LocalDate desde, LocalDate hasta, boolean incluirEliminadas,
                                                int pagina, int tamano) {
        List<Specification<SiigoCotizacion>> filtros = new ArrayList<>();
        if (!incluirEliminadas) {
            filtros.add((root, query, cb) -> cb.isFalse(root.get("eliminadaEnSiigo")));
        }
        if (estado == EstadoSiigoCotizacion.POR_APROBAR) {
            // Las filas viejas pueden tener estado null, que también significa POR_APROBAR
            filtros.add((root, query, cb) -> cb.or(cb.isNull(root.get("estado")), cb.equal(root.get("estado"), estado)));
        } else if (estado != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (responsableId != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("responsable").get("id"), responsableId));
        }
        if (desde != null) {
            filtros.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde));
        }
        if (hasta != null) {
            filtros.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), hasta));
        }
        if (texto != null && !texto.isBlank()) {
            String t = texto.trim().toLowerCase();
            String patron = "%" + t + "%";
            List<String> nitsEmpresa = clientes.findAll().stream()
                    .filter(c -> c.getNombre().toLowerCase().contains(t))
                    .map(Cliente::getNit)
                    .toList();
            filtros.add((root, query, cb) -> {
                var condiciones = new ArrayList<jakarta.persistence.criteria.Predicate>();
                for (String campo : List.of("numero", "clienteNit", "descripcion", "descripcionSiigo",
                        "ingenieroSolicitante", "ingenieroSiigo", "ordenCompra", "numeroFactura")) {
                    condiciones.add(cb.like(cb.lower(root.get(campo)), patron));
                }
                if (!nitsEmpresa.isEmpty()) {
                    condiciones.add(root.get("clienteNit").in(nitsEmpresa));
                }
                return cb.or(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
            });
        }

        Specification<SiigoCotizacion> spec = filtros.stream().reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
        PageRequest orden = PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamano), 200),
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("consecutivo")));
        Page<SiigoCotizacion> page = cotizaciones.findAll(spec, orden);

        Map<String, String> empresas = empresasDe(page.getContent());
        return PaginaDTO.desde(page, c -> SiigoCotizacionDTO.desde(c, empresas.get(c.getClienteNit()), false, null));
    }

    @Transactional(readOnly = true)
    public SiigoCotizacionDTO obtener(Long id) {
        return detalle(buscarCotizacion(id));
    }

    // ------------------------------------------------------------------ control

    @Transactional
    public SiigoCotizacionDTO actualizarControl(Long id, ControlSiigoCotizacionRequest r, String actor) {
        if (r == null) {
            throw error(HttpStatus.BAD_REQUEST, "El cuerpo de la petición es obligatorio");
        }
        noNegativo(r.valorOc(), "valorOc");
        noNegativo(r.valorPagado(), "valorPagado");
        Usuario responsable = null;
        if (r.responsableId() != null) {
            responsable = usuarios.findById(r.responsableId())
                    .filter(u -> u.estaActivo() && u.getRol() != null)
                    .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "El responsable no existe o no está activo"));
        }
        SiigoCotizacion c = buscarCotizacion(id);
        c.actualizarControl(
                limpio(r.descripcion(), 500, "descripcion"),
                limpio(r.ingenieroSolicitante(), 150, "ingenieroSolicitante"),
                responsable,
                limpio(r.ordenCompra(), 50, "ordenCompra"),
                r.valorOc(),
                limpio(r.numeroFactura(), 50, "numeroFactura"),
                r.fechaFactura(),
                r.fechaPago(),
                limpio(r.comprobantePago(), 100, "comprobantePago"),
                r.valorPagado(),
                actor);
        auditoria.registrar("EDITAR_CONTROL_COTIZACION_SIIGO", ENTIDAD, id, c.getNumero());
        return detalle(c);
    }

    @Transactional
    public SiigoCotizacionDTO cambiarEstado(Long id, EstadoSiigoCotizacion nuevo, String actor) {
        if (nuevo == null) {
            throw error(HttpStatus.BAD_REQUEST,
                    "El campo 'estado' es obligatorio (POR_APROBAR, POR_EJECUTAR, POR_FACTURAR, PAGADA o NO_APROBADA)");
        }
        SiigoCotizacion c = buscarCotizacion(id);
        EstadoSiigoCotizacion anterior = c.getEstado();
        c.cambiarEstado(nuevo, actor);
        auditoria.registrar("CAMBIAR_ESTADO_COTIZACION_SIIGO", ENTIDAD, id,
                c.getNumero() + ": " + anterior + " -> " + nuevo);
        return detalle(c);
    }

    // ------------------------------------------------------------------ utilidades

    private SiigoCotizacionDTO detalle(SiigoCotizacion c) {
        JsonNode items = mapper.createArrayNode();
        if (c.getItemsJson() != null) {
            try {
                items = mapper.readTree(c.getItemsJson());
            } catch (JsonProcessingException e) {
                // items corruptos: se devuelve la lista vacía
            }
        }
        String empresa = c.getClienteNit() == null ? null
                : clientes.findByNit(c.getClienteNit()).map(Cliente::getNombre).orElse(null);
        return SiigoCotizacionDTO.desde(c, empresa, true, items);
    }

    private Map<String, String> empresasDe(List<SiigoCotizacion> lista) {
        List<String> nits = lista.stream().map(SiigoCotizacion::getClienteNit).filter(Objects::nonNull)
                .distinct().toList();
        if (nits.isEmpty()) {
            return Map.of();
        }
        return clientes.findByNitIn(nits).stream()
                .collect(Collectors.toMap(Cliente::getNit, Cliente::getNombre, (a, b) -> a));
    }

    private SiigoCotizacion buscarCotizacion(Long id) {
        return cotizaciones.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Cotización no encontrada"));
    }

    private static String limpio(String valor, int max, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.strip();
        if (v.length() > max) {
            throw error(HttpStatus.BAD_REQUEST, "'" + campo + "' admite máximo " + max + " caracteres");
        }
        return v;
    }

    private static void noNegativo(BigDecimal v, String campo) {
        if (v != null && v.signum() < 0) {
            throw error(HttpStatus.BAD_REQUEST, "'" + campo + "' no puede ser negativo");
        }
    }

    private static ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
