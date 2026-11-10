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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
     * Recorre todas las páginas de un recurso y junta los resultados.
     * Para en la última página o al llegar a MAX_PAGINAS (avisa con "incompleto": true).
     */
    public JsonNode listarTodo(String recurso, Map<String, String> filtros) {
        validarRecurso(recurso);
        ArrayNode resultados = mapper.createArrayNode();
        Integer total = null;
        int pagina = 1;
        boolean incompleto = false;

        while (true) {
            Map<String, String> params = new LinkedHashMap<>(filtros);
            params.put("page", String.valueOf(pagina));
            params.put("page_size", String.valueOf(TAMANO_PAGINA));
            JsonNode respuesta = listar(recurso, params);

            // Los catálogos (taxes, users...) devuelven un arreglo directo, sin paginación
            if (respuesta.isArray()) {
                resultados.addAll((ArrayNode) respuesta);
                total = resultados.size();
                break;
            }

            JsonNode lote = respuesta.path("results");
            if (lote.isArray()) {
                resultados.addAll((ArrayNode) lote);
            }
            JsonNode totalNodo = respuesta.path("pagination").path("total_results");
            if (totalNodo.isNumber()) {
                total = totalNodo.asInt();
            }

            boolean ultima = !lote.isArray() || lote.isEmpty() || lote.size() < TAMANO_PAGINA
                    || (total != null && resultados.size() >= total);
            if (ultima) {
                break;
            }
            if (pagina >= MAX_PAGINAS) {
                incompleto = true;
                break;
            }
            pagina++;
        }

        ObjectNode salida = mapper.createObjectNode();
        salida.put("recurso", recurso);
        salida.put("paginasConsultadas", pagina);
        if (total != null) {
            salida.put("totalEnSiigo", total);
        }
        salida.put("cantidad", resultados.size());
        salida.put("incompleto", incompleto);
        salida.set("results", resultados);
        return salida;
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
