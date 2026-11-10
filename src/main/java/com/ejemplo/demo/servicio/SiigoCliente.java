package com.ejemplo.demo.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Cliente TEMPORAL de la API de Siigo Nube, para explorar qué datos ofrece
 * mientras se define cómo manejar la información. Devuelve el JSON tal como llega.
 *
 * Autenticación: POST /auth con usuario y access_key -> access_token (se guarda en
 * memoria hasta poco antes de vencer). Todas las peticiones llevan el header Partner-Id.
 *
 * Límites de Siigo: 100 peticiones/minuto por empresa (10 en la empresa de pruebas).
 * Si más del 80% de las peticiones fallan durante 7 días, Siigo bloquea el usuario API.
 */
@Service
public class SiigoCliente {

    private static final Logger log = LoggerFactory.getLogger(SiigoCliente.class);

    /** Recursos que se pueden consultar. Lista cerrada para no reenviar rutas arbitrarias. */
    public static final Set<String> RECURSOS = Set.of(
            "quotations", "customers", "products", "invoices", "credit-notes", "vouchers",
            "payment-receipts", "purchases", "journals", "purchase-support-documents",
            "document-types", "taxes", "payment-types", "users", "warehouses",
            "cost-centers", "price-lists", "account-groups", "fixed-assets");

    /** Máximo de registros por página que acepta Siigo. */
    static final int TAMANO_PAGINA = 100;
    /** Tope de páginas al traer "todo", para no agotar el límite de peticiones. */
    static final int MAX_PAGINAS = 50;
    private static final int MAX_REINTENTOS = 4;
    /** Páginas que se piden a Siigo al mismo tiempo. */
    static final int PARALELAS = 4;
    static final int CACHE_MINUTOS = 10;

    private final Map<String, Cacheado> cache = new ConcurrentHashMap<>();

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String usuario;
    private final String accessKey;
    private final String partnerId;

    private String token;
    private Instant tokenVence = Instant.EPOCH;

    public SiigoCliente(@Value("${siigo.url:https://api.siigo.com}") String url,
                        @Value("${siigo.username:}") String usuario,
                        @Value("${siigo.access-key:}") String accessKey,
                        @Value("${siigo.partner-id:SIEV}") String partnerId,
                        ObjectMapper mapper) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(15));
        fabrica.setReadTimeout(Duration.ofSeconds(120)); // recomendado por Siigo
        this.http = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
        this.mapper = mapper;
        this.usuario = usuario;
        this.accessKey = accessKey;
        this.partnerId = partnerId;
    }

    /** true si hay usuario y access_key de Siigo. */
    public boolean configurado() {
        return !usuario.isBlank() && !accessKey.isBlank();
    }

    /** Una página de un recurso. Los filtros (page, page_size, created_start...) se reenvían tal cual. */
    public JsonNode listar(String recurso, Map<String, String> filtros) {
        validarRecurso(recurso);
        return get(uri -> {
            UriBuilder b = uri.path("/v1/" + recurso);
            filtros.forEach(b::queryParam);
            return b.build();
        });
    }

    /** Un registro por su id. */
    public JsonNode obtener(String recurso, String id) {
        validarRecurso(recurso);
        return get(uri -> uri.path("/v1/" + recurso + "/{id}").build(id));
    }

    /**
     * Trae todos los registros de un recurso.
     *
     * - La 1.ª página dice cuántos hay; el resto se pide EN PARALELO (PARALELAS a la vez),
     *   sin pasar del límite de Siigo de 100 peticiones/minuto.
     * - El resultado se guarda en memoria CACHE_MINUTOS para no repetir la consulta;
     *   refrescar=true la fuerza.
     * - resumen=true quita los ítems (deja "cantidadItems") para que la respuesta pese poco.
     * - Se detiene en MAX_PAGINAS (avisa con "incompleto": true).
     */
    public JsonNode listarTodo(String recurso, Map<String, String> filtros, boolean resumen, boolean refrescar) {
        validarRecurso(recurso);
        String clave = recurso + filtros;
        Cacheado cacheado = cache.get(clave);
        boolean desdeCache = true;
        if (refrescar || cacheado == null || cacheado.vence().isBefore(Instant.now())) {
            cacheado = new Cacheado(consultarTodo(recurso, filtros),
                    Instant.now().plus(Duration.ofMinutes(CACHE_MINUTOS)));
            cache.put(clave, cacheado);
            desdeCache = false;
        }

        ObjectNode salida = cacheado.datos().deepCopy();
        salida.put("desdeCache", desdeCache);
        if (resumen) {
            for (JsonNode r : salida.withArray("results")) {
                if (r instanceof ObjectNode o && o.path("items").isArray()) {
                    o.put("cantidadItems", o.path("items").size());
                    o.remove("items");
                }
            }
        }
        return salida;
    }

    private ObjectNode consultarTodo(String recurso, Map<String, String> filtros) {
        long inicio = System.currentTimeMillis();
        JsonNode primera = pagina(recurso, filtros, 1);
        ArrayNode resultados = mapper.createArrayNode();
        Integer total = null;
        int paginas = 1;
        boolean incompleto = false;

        if (primera.isArray()) {
            // Los catálogos (taxes, users...) devuelven un arreglo directo, sin paginación
            resultados.addAll((ArrayNode) primera);
            total = resultados.size();
        } else {
            JsonNode lote = primera.path("results");
            if (lote.isArray()) {
                resultados.addAll((ArrayNode) lote);
            }
            JsonNode totalNodo = primera.path("pagination").path("total_results");
            if (totalNodo.isNumber()) {
                total = totalNodo.asInt();
                // Usar el tamaño que Siigo realmente aplicó, por si limita page_size por debajo de 100
                int tamano = primera.path("pagination").path("page_size").asInt(TAMANO_PAGINA);
                int necesarias = (int) Math.ceil(total / (double) Math.max(1, tamano));
                paginas = Math.min(necesarias, MAX_PAGINAS);
                incompleto = necesarias > MAX_PAGINAS;
            }

            if (paginas > 1) {
                ExecutorService hilos = Executors.newFixedThreadPool(PARALELAS);
                try {
                    List<Future<JsonNode>> futuros = new ArrayList<>();
                    for (int p = 2; p <= paginas; p++) {
                        int numero = p;
                        futuros.add(hilos.submit(() -> pagina(recurso, filtros, numero)));
                    }
                    for (Future<JsonNode> f : futuros) { // en orden de página
                        JsonNode loteN = f.get().path("results");
                        if (loteN.isArray()) {
                            resultados.addAll((ArrayNode) loteN);
                        }
                    }
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error consultando Siigo");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta a Siigo interrumpida");
                } finally {
                    hilos.shutdownNow();
                }
            }
        }

        long ms = System.currentTimeMillis() - inicio;
        log.info("Siigo {}: {} registros en {} páginas, {} ms", recurso, resultados.size(), paginas, ms);

        ObjectNode salida = mapper.createObjectNode();
        salida.put("recurso", recurso);
        salida.put("paginasConsultadas", paginas);
        if (total != null) {
            salida.put("totalEnSiigo", total);
        }
        salida.put("cantidad", resultados.size());
        salida.put("incompleto", incompleto);
        salida.put("segundos", ms / 1000.0);
        salida.put("consultadoEn", Instant.now().toString());
        salida.set("results", resultados);
        return salida;
    }

    private JsonNode pagina(String recurso, Map<String, String> filtros, int numero) {
        Map<String, String> params = new LinkedHashMap<>(filtros);
        params.put("page", String.valueOf(numero));
        params.put("page_size", String.valueOf(TAMANO_PAGINA));
        long inicio = System.currentTimeMillis();
        JsonNode respuesta = listar(recurso, params);
        log.debug("Siigo {} página {}: {} ms", recurso, numero, System.currentTimeMillis() - inicio);
        return respuesta;
    }

    /** Vacía la caché (por ejemplo, después de crear cotizaciones en Siigo). */
    public void limpiarCache() {
        cache.clear();
    }

    private record Cacheado(ObjectNode datos, Instant vence) {
    }

    // ------------------------------------------------------------------

    private JsonNode get(java.util.function.Function<UriBuilder, URI> uri) {
        boolean tokenRenovado = false;
        for (int intento = 0; ; intento++) {
            try {
                return http.get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + token())
                        .header("Partner-Id", partnerId)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException e) {
                int estado = e.getStatusCode().value();
                if (estado == 401 && !tokenRenovado) {
                    // Token vencido o revocado: pedir uno nuevo y reintentar una vez
                    invalidarToken();
                    tokenRenovado = true;
                    continue;
                }
                if (estado == 429 && intento < MAX_REINTENTOS) {
                    esperar(intento);
                    continue;
                }
                throw errorDeSiigo(e);
            }
        }
    }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenVence)) {
            return token;
        }
        if (usuario.isBlank() || accessKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Siigo no está configurado: define las variables SIIGO_USERNAME y SIIGO_ACCESS_KEY");
        }
        try {
            JsonNode respuesta = http.post()
                    .uri("/auth")
                    .header("Partner-Id", partnerId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", usuario, "access_key", accessKey))
                    .retrieve()
                    .body(JsonNode.class);
            String nuevo = respuesta == null ? null : respuesta.path("access_token").asText(null);
            if (nuevo == null || nuevo.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Siigo no devolvió access_token");
            }
            long segundos = respuesta.path("expires_in").asLong(86400);
            token = nuevo;
            // Margen de 5 minutos para no usar un token a punto de vencer
            tokenVence = Instant.now().plusSeconds(Math.max(60, segundos - 300));
            log.info("Token de Siigo obtenido, vence en {} s", segundos);
            return token;
        } catch (RestClientResponseException e) {
            throw errorDeSiigo(e);
        }
    }

    private synchronized void invalidarToken() {
        token = null;
        tokenVence = Instant.EPOCH;
    }

    private static void esperar(int intento) {
        long ms = 2000L << intento; // 2s, 4s, 8s, 16s
        log.warn("Siigo respondió 429 (límite de peticiones). Reintentando en {} ms", ms);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta a Siigo interrumpida");
        }
    }

    private static ResponseStatusException errorDeSiigo(RestClientResponseException e) {
        int estado = e.getStatusCode().value();
        String detalle = e.getResponseBodyAsString();
        log.warn("Error de Siigo {}: {}", estado, detalle);
        HttpStatus nuestro = switch (estado) {
            case 404 -> HttpStatus.NOT_FOUND;
            case 400 -> HttpStatus.BAD_REQUEST;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(nuestro, "Siigo respondió " + estado + ": " + detalle);
    }

    private static void validarRecurso(String recurso) {
        if (!RECURSOS.contains(recurso)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Recurso no permitido: " + recurso + ". Usa uno de: " + RECURSOS);
        }
    }
}
