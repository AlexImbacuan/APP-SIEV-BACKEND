package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.dto.SiigoCotizacionDTO;
import com.ejemplo.demo.modelo.SiigoCotizacion;
import com.ejemplo.demo.modelo.SincronizacionSiigo;
import com.ejemplo.demo.repositorio.SiigoCotizacionRepositorio;
import com.ejemplo.demo.repositorio.SincronizacionSiigoRepositorio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Copia las cotizaciones de Siigo a la tabla siigo_cotizacion.
 *
 * Cuándo corre:
 *  - Automática: siigo.sincronizacion.cron (por defecto 6:00 y 13:00, hora de Colombia).
 *  - Al arrancar: si la última sincronización exitosa tiene más de HORAS_VIGENCIA horas.
 *    Cubre el caso de Render apagando la app y perdiéndose la hora programada.
 *  - Manual: POST /api/siigo-cotizaciones/sincronizar.
 *
 * Siempre trae todo (≈17 peticiones, ~15 s): así detecta cambios y cotizaciones borradas en Siigo.
 */
@Service
public class SiigoSincronizacionServicio {

    private static final Logger log = LoggerFactory.getLogger(SiigoSincronizacionServicio.class);
    static final String RECURSO = "quotations";
    static final int HORAS_VIGENCIA = 12;

    private final SiigoCliente siigo;
    private final SiigoCotizacionRepositorio cotizaciones;
    private final SincronizacionSiigoRepositorio sincronizaciones;
    private final TransactionTemplate transaccion;
    private final ObjectMapper mapper;
    private final boolean alArrancar;

    /** Evita dos sincronizaciones al mismo tiempo (la programada y una manual, por ejemplo). */
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public SiigoSincronizacionServicio(SiigoCliente siigo, SiigoCotizacionRepositorio cotizaciones,
                                       SincronizacionSiigoRepositorio sincronizaciones,
                                       TransactionTemplate transaccion, ObjectMapper mapper,
                                       @Value("${siigo.sincronizacion.al-arrancar:true}") boolean alArrancar) {
        this.siigo = siigo;
        this.cotizaciones = cotizaciones;
        this.sincronizaciones = sincronizaciones;
        this.transaccion = transaccion;
        this.mapper = mapper;
        this.alArrancar = alArrancar;
    }

    // ---------------------------------------------------------------- disparadores

    @Scheduled(cron = "${siigo.sincronizacion.cron:0 0 6,13 * * *}", zone = "America/Bogota")
    public void programada() {
        sincronizarSiConfigurado("AUTOMATICA");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!alArrancar || !siigo.configurado()) {
            return;
        }
        boolean vigente = sincronizaciones.findFirstByRecursoAndExitosaTrueOrderByInicioDesc(RECURSO)
                .map(s -> s.getInicio().isAfter(LocalDateTime.now().minusHours(HORAS_VIGENCIA)))
                .orElse(false);
        if (!vigente) {
            // En otro hilo para no demorar el arranque
            Thread.ofVirtual().name("siigo-sync-arranque").start(() -> sincronizarSiConfigurado("AL_ARRANCAR"));
        }
    }

    private void sincronizarSiConfigurado(String origen) {
        if (!siigo.configurado()) {
            log.info("Sincronización con Siigo omitida: credenciales no configuradas");
            return;
        }
        try {
            sincronizar(origen);
        } catch (Exception e) {
            // Ya quedó registrada como fallida; no tumbar el hilo del scheduler
            log.error("Falló la sincronización {} con Siigo: {}", origen, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- sincronización

    public SincronizacionSiigo sincronizar(String origen) {
        if (!enCurso.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya hay una sincronización con Siigo en curso. Intenta en un minuto.");
        }
        SincronizacionSiigo registro = new SincronizacionSiigo(RECURSO, origen);
        try {
            // Fuera de la transacción: la consulta a Siigo tarda varios segundos
            JsonNode respuesta = siigo.listarTodo(RECURSO, Map.of(), false, true);
            boolean incompleto = respuesta.path("incompleto").asBoolean(false);
            JsonNode resultados = respuesta.path("results");

            int[] conteo = transaccion.execute(estado -> guardar(resultados, !incompleto));
            registro.terminarBien(resultados.size(), conteo[0], conteo[1], conteo[2]);
            log.info("Sincronización Siigo ({}): {} en Siigo, {} nuevas, {} actualizadas, {} eliminadas",
                    origen, resultados.size(), conteo[0], conteo[1], conteo[2]);
            return sincronizaciones.save(registro);
        } catch (RuntimeException e) {
            registro.terminarMal(e instanceof ResponseStatusException r ? r.getReason() : e.getMessage());
            sincronizaciones.save(registro);
            throw e;
        } finally {
            enCurso.set(false);
        }
    }

    /** @return {nuevas, actualizadas, eliminadas} */
    private int[] guardar(JsonNode resultados, boolean marcarEliminadas) {
        Map<String, SiigoCotizacion> existentes = cotizaciones.findAll().stream()
                .collect(Collectors.toMap(SiigoCotizacion::getSiigoId, Function.identity()));
        Set<String> vistos = new HashSet<>();
        int nuevas = 0;
        int actualizadas = 0;
        int eliminadas = 0;

        for (JsonNode q : resultados) {
            String siigoId = texto(q, "id");
            if (siigoId == null || !vistos.add(siigoId)) {
                continue;
            }
            SiigoCotizacion c = existentes.get(siigoId);
            boolean esNueva = c == null;
            if (esNueva) {
                c = new SiigoCotizacion();
                c.setSiigoId(siigoId);
            }
            JsonNode items = q.path("items");
            boolean cambio = c.aplicar(
                    texto(q, "name") != null ? texto(q, "name") : "SIN-NUMERO",
                    q.path("number").isNumber() ? q.path("number").asInt() : null,
                    fecha(texto(q, "date")),
                    texto(q.path("customer"), "identification"),
                    texto(q.path("customer"), "id"),
                    q.path("seller").isNumber() ? q.path("seller").asLong() : null,
                    q.path("total").isNumber() ? q.path("total").decimalValue() : null,
                    items.isArray() ? items.size() : 0,
                    items.isArray() ? json(items) : null,
                    texto(q, "public_url"),
                    fechaHora(texto(q.path("metadata"), "created")),
                    fechaHora(texto(q.path("metadata"), "last_updated")));
            if (esNueva) {
                cotizaciones.save(c);
                nuevas++;
            } else if (cambio) {
                actualizadas++;
            }
        }

        // Si la consulta vino incompleta no se puede saber cuáles se borraron
        if (marcarEliminadas) {
            for (SiigoCotizacion c : existentes.values()) {
                if (!vistos.contains(c.getSiigoId()) && c.marcarEliminada()) {
                    eliminadas++;
                }
            }
        }
        return new int[]{nuevas, actualizadas, eliminadas};
    }

    // ---------------------------------------------------------------- consultas locales

    public PaginaDTO<SiigoCotizacionDTO> buscar(String texto, LocalDate desde, LocalDate hasta,
                                                boolean incluirEliminadas, int pagina, int tamano) {
        Specification<SiigoCotizacion> spec = (root, query, cb) -> cb.conjunction();
        if (!incluirEliminadas) {
            spec = spec.and((root, query, cb) -> cb.isFalse(root.get("eliminadaEnSiigo")));
        }
        if (texto != null && !texto.isBlank()) {
            String patron = "%" + texto.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("numero")), patron),
                    cb.like(cb.lower(root.get("clienteNit")), patron)));
        }
        if (desde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde));
        }
        if (hasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fecha"), hasta));
        }
        PageRequest orden = PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamano), 200),
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("consecutivo")));
        return PaginaDTO.desde(cotizaciones.findAll(spec, orden), c -> SiigoCotizacionDTO.desde(c, null));
    }

    public SiigoCotizacionDTO obtener(Long id) {
        SiigoCotizacion c = cotizaciones.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cotización no encontrada"));
        JsonNode items = mapper.createArrayNode();
        if (c.getItemsJson() != null) {
            try {
                items = mapper.readTree(c.getItemsJson());
            } catch (JsonProcessingException e) {
                log.warn("items inválidos en siigo_cotizacion {}", id);
            }
        }
        return SiigoCotizacionDTO.desde(c, items);
    }

    public Map<String, Object> estado() {
        Map<String, Object> salida = new HashMap<>();
        salida.put("configurado", siigo.configurado());
        salida.put("enCurso", enCurso.get());
        salida.put("cotizacionesGuardadas", cotizaciones.count());
        sincronizaciones.findFirstByRecursoOrderByInicioDesc(RECURSO).ifPresent(s -> salida.put("ultima", s));
        sincronizaciones.findFirstByRecursoAndExitosaTrueOrderByInicioDesc(RECURSO)
                .ifPresent(s -> salida.put("ultimaExitosa", s));
        return salida;
    }

    public List<SincronizacionSiigo> historial() {
        return sincronizaciones.findTop20ByRecursoOrderByInicioDesc(RECURSO);
    }

    // ---------------------------------------------------------------- utilidades

    private static String texto(JsonNode nodo, String campo) {
        JsonNode v = nodo.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static LocalDate fecha(String valor) {
        try {
            return valor == null ? null : LocalDate.parse(valor.length() > 10 ? valor.substring(0, 10) : valor);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDateTime fechaHora(String valor) {
        try {
            return valor == null ? null : LocalDateTime.parse(valor.endsWith("Z")
                    ? valor.substring(0, valor.length() - 1) : valor);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String json(JsonNode nodo) {
        try {
            return mapper.writeValueAsString(nodo);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
