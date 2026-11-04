package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.CambiarEstadoCotizacionRequest;
import com.ejemplo.demo.dto.CotizacionDTO;
import com.ejemplo.demo.dto.CotizacionLeidaDTO;
import com.ejemplo.demo.dto.CotizacionRequest;
import com.ejemplo.demo.dto.CotizacionResumenDTO;
import com.ejemplo.demo.dto.ItemCotizacionDTO;
import com.ejemplo.demo.dto.PaginaDTO;
import com.ejemplo.demo.dto.ResponsableDTO;
import com.ejemplo.demo.modelo.Cotizacion;
import com.ejemplo.demo.modelo.EstadoCotizacion;
import com.ejemplo.demo.modelo.ItemCotizacion;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.CotizacionRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.ejemplo.demo.servicio.AuditoriaServicio.ENTIDAD_COTIZACION;

@Service
@Transactional
public class CotizacionServicio {

    /** IVA general en Colombia. Solo se usa para calcular el IVA si no se envía y para los avisos. */
    static final BigDecimal TASA_IVA = new BigDecimal("0.19");

    /** Diferencia máxima (en pesos) que se acepta por redondeos antes de mostrar un aviso. */
    private static final BigDecimal TOLERANCIA = BigDecimal.ONE;

    private static final int TAMANO_MAXIMO = 100;

    private final CotizacionRepositorio cotizacionRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final LectorCotizacionPdf lectorPdf;
    private final AuditoriaServicio auditoria;

    public CotizacionServicio(CotizacionRepositorio cotizacionRepositorio,
                              UsuarioRepositorio usuarioRepositorio,
                              LectorCotizacionPdf lectorPdf,
                              AuditoriaServicio auditoria) {
        this.cotizacionRepositorio = cotizacionRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.lectorPdf = lectorPdf;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------ consultas

    /** Lista paginada, de la más reciente a la más antigua. Todos los filtros son opcionales. */
    @Transactional(readOnly = true)
    public PaginaDTO<CotizacionResumenDTO> buscar(EstadoCotizacion estado, Long responsableId, Boolean facturada,
                                                  String texto, int pagina, int tamano) {

        Specification<Cotizacion> filtro = (root, query, cb) -> cb.conjunction();

        if (estado != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (responsableId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("responsable").get("id"), responsableId));
        }
        if (facturada != null) {
            filtro = filtro.and((root, query, cb) -> {
                var numeroFactura = root.<String>get("numeroFactura");
                var conFactura = cb.and(cb.isNotNull(numeroFactura), cb.notEqual(cb.trim(numeroFactura), ""));
                return facturada ? conFactura : cb.not(conFactura);
            });
        }
        if (texto != null && !texto.isBlank()) {
            String patron = "%" + texto.trim().toLowerCase() + "%";
            filtro = filtro.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("numero")), patron),
                    cb.like(cb.lower(root.get("cliente")), patron),
                    cb.like(cb.lower(root.get("ingSolicitante")), patron)));
        }

        PageRequest paginacion = PageRequest.of(
                Math.max(pagina, 0),
                Math.min(Math.max(tamano, 1), TAMANO_MAXIMO),
                Sort.by(Sort.Order.desc("id")));

        return PaginaDTO.desde(cotizacionRepositorio.findAll(filtro, paginacion), CotizacionResumenDTO::desde);
    }

    @Transactional(readOnly = true)
    public CotizacionDTO obtener(Long id) {
        return aDTO(buscarCotizacion(id));
    }

    /** Usuarios activos con rol, para escoger el responsable SIEV. */
    @Transactional(readOnly = true)
    public List<ResponsableDTO> responsables() {
        return usuarioRepositorio.findAll().stream()
                .filter(u -> u.estaActivo() && u.getRol() != null)
                .sorted(Comparator.comparing(u -> Objects.toString(u.getNombre(), ""), String.CASE_INSENSITIVE_ORDER))
                .map(ResponsableDTO::desde)
                .toList();
    }

    // ------------------------------------------------------------------ PDF

    /** Lee un PDF de Siigo y devuelve los datos para llenar el formulario. No guarda nada. */
    @Transactional(readOnly = true)
    public CotizacionLeidaDTO leerPdf(byte[] pdf) {

        LectorCotizacionPdf.Resultado r = lectorPdf.leer(pdf);
        List<String> avisos = new ArrayList<>();

        if (r.numero() == null) avisos.add("No se encontró el número de la cotización");
        if (r.cliente() == null) avisos.add("No se encontró la empresa cliente");
        if (r.ingSolicitante() == null) avisos.add("No se encontró el ingeniero solicitante (línea \"ING:\")");
        if (r.items().isEmpty()) avisos.add("No se encontraron materiales en la tabla");
        if (r.subtotal() == null) avisos.add("No se encontró el subtotal");
        if (r.iva() == null) avisos.add("No se encontró el IVA");
        if (r.total() == null) avisos.add("No se encontró el total");

        Long idExistente = null;
        if (r.numero() != null) {
            idExistente = cotizacionRepositorio.findByNumeroIgnoreCase(r.numero()).map(Cotizacion::getId).orElse(null);
            if (idExistente != null) {
                avisos.add("Ya existe una cotización con el número " + r.numero());
            }
        }

        avisos.addAll(revisarValores(r.items(), r.subtotal(), r.iva(), r.total()));

        return new CotizacionLeidaDTO(r.numero(), r.cliente(), r.ingSolicitante(), r.items(),
                r.subtotal(), r.iva(), r.total(), avisos, idExistente);
    }

    // ------------------------------------------------------------------ cambios

    public CotizacionDTO crear(CotizacionRequest request) {

        Cotizacion cotizacion = new Cotizacion();
        aplicar(cotizacion, request);
        cotizacion.setEstado(EstadoCotizacion.PENDIENTE);
        cotizacionRepositorio.save(cotizacion);

        auditoria.registrar("CREAR_COTIZACION", ENTIDAD_COTIZACION, cotizacion.getId(),
                cotizacion.getNumero() + " - " + cotizacion.getCliente() + " - total " + pesos(cotizacion.getTotal()));

        return aDTO(cotizacion);
    }

    /** Reemplaza todos los datos (incluidos los materiales). El estado no cambia aquí. */
    public CotizacionDTO actualizar(Long id, CotizacionRequest request) {

        Cotizacion cotizacion = buscarCotizacion(id);
        String facturaAnterior = cotizacion.getNumeroFactura();

        aplicar(cotizacion, request);

        String detalle = cotizacion.getNumero();
        if (!Objects.equals(facturaAnterior, cotizacion.getNumeroFactura())) {
            detalle += "; factura: " + Objects.toString(facturaAnterior, "(ninguna)")
                    + " -> " + Objects.toString(cotizacion.getNumeroFactura(), "(ninguna)");
        }
        auditoria.registrar("EDITAR_COTIZACION", ENTIDAD_COTIZACION, id, detalle);

        return aDTO(cotizacion);
    }

    public CotizacionDTO cambiarEstado(Long id, CambiarEstadoCotizacionRequest request) {

        if (request == null || request.estado() == null) {
            throw error(HttpStatus.BAD_REQUEST,
                    "El campo 'estado' es obligatorio (PENDIENTE, APROBADA o NO_APROBADA)");
        }

        Cotizacion cotizacion = buscarCotizacion(id);
        EstadoCotizacion anterior = cotizacion.getEstado();

        if (request.estado() == EstadoCotizacion.NO_APROBADA) {
            cotizacion.setMotivoRechazo(texto(obligatorio(request.motivoRechazo(), "motivoRechazo"),
                    "motivoRechazo", 1000));
        } else {
            cotizacion.setMotivoRechazo(null);
        }
        cotizacion.setEstado(request.estado());

        auditoria.registrar("CAMBIAR_ESTADO_COTIZACION", ENTIDAD_COTIZACION, id,
                cotizacion.getNumero() + ": " + anterior + " -> " + request.estado()
                        + (cotizacion.getMotivoRechazo() != null ? " (" + cotizacion.getMotivoRechazo() + ")" : ""));

        return aDTO(cotizacion);
    }

    public void eliminar(Long id) {
        Cotizacion cotizacion = buscarCotizacion(id);
        cotizacionRepositorio.delete(cotizacion);
        auditoria.registrar("ELIMINAR_COTIZACION", ENTIDAD_COTIZACION, id,
                cotizacion.getNumero() + " - " + cotizacion.getCliente());
    }

    // ------------------------------------------------------------------ reglas

    /** Valida el request y copia sus datos a la cotización (nueva o existente). */
    private void aplicar(Cotizacion cotizacion, CotizacionRequest request) {

        if (request == null) {
            throw error(HttpStatus.BAD_REQUEST, "Faltan los datos de la cotización");
        }

        String numero = texto(obligatorio(request.numero(), "numero"), "numero", 50);
        cotizacionRepositorio.findByNumeroIgnoreCase(numero)
                .filter(otra -> !otra.getId().equals(cotizacion.getId()))
                .ifPresent(otra -> {
                    throw error(HttpStatus.CONFLICT, "Ya existe una cotización con el número " + numero);
                });

        cotizacion.setNumero(numero);
        cotizacion.setCliente(texto(obligatorio(request.cliente(), "cliente"), "cliente", 255));
        cotizacion.setIngSolicitante(texto(vacioANull(request.ingSolicitante()), "ingSolicitante", 255));
        cotizacion.setResponsable(responsable(request.responsableId(), cotizacion.getResponsable()));
        cotizacion.setNumeroFactura(texto(vacioANull(request.numeroFactura()), "numeroFactura", 50));
        cotizacion.setNotas(vacioANull(request.notas()));

        List<ItemCotizacion> items = new ArrayList<>();
        List<ItemCotizacionDTO> recibidos = request.items() != null ? request.items() : List.of();
        for (int i = 0; i < recibidos.size(); i++) {
            items.add(item(recibidos.get(i), i + 1));
        }
        cotizacion.reemplazarItems(items);

        BigDecimal subtotal = request.subtotal() != null
                ? noNegativo(request.subtotal(), "subtotal")
                : items.stream().map(ItemCotizacion::getValorBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal iva = request.iva() != null
                ? noNegativo(request.iva(), "iva")
                : subtotal.multiply(TASA_IVA).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = request.total() != null
                ? noNegativo(request.total(), "total")
                : subtotal.add(iva);

        cotizacion.setSubtotal(subtotal);
        cotizacion.setIva(iva);
        cotizacion.setTotal(total);
    }

    private ItemCotizacion item(ItemCotizacionDTO dto, int posicion) {

        if (dto == null) {
            throw error(HttpStatus.BAD_REQUEST, "El material " + posicion + " está vacío");
        }
        String campo = "items[" + posicion + "].";

        ItemCotizacion item = new ItemCotizacion();
        item.setDescripcion(texto(obligatorio(dto.descripcion(), campo + "descripcion"), campo + "descripcion", 500));
        item.setCantidad(noNegativo(obligatorio(dto.cantidad(), campo + "cantidad"), campo + "cantidad"));
        item.setValorUnitario(noNegativo(obligatorio(dto.valorUnitario(), campo + "valorUnitario"),
                campo + "valorUnitario"));
        item.setValorBruto(dto.valorBruto() != null
                ? noNegativo(dto.valorBruto(), campo + "valorBruto")
                : item.getCantidad().multiply(item.getValorUnitario()).setScale(2, RoundingMode.HALF_UP));
        return item;
    }

    /** null quita el responsable. Un usuario desactivado o sin rol solo se acepta si ya era el responsable. */
    private Usuario responsable(Long responsableId, Usuario actual) {

        if (responsableId == null) {
            return null;
        }
        if (actual != null && actual.getId().equals(responsableId)) {
            return actual;
        }

        Usuario usuario = usuarioRepositorio.findById(responsableId)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "El responsable indicado no existe"));

        if (!usuario.estaActivo() || usuario.getRol() == null) {
            throw error(HttpStatus.BAD_REQUEST, "El responsable debe ser un usuario activo y con rol");
        }
        return usuario;
    }

    /**
     * Revisa que los valores cuadren. Devuelve avisos (no errores): la cotización se guarda igual,
     * porque puede haber casos válidos que no sigan la regla (ej. IVA sobre AIU).
     */
    static List<String> revisarValores(List<ItemCotizacionDTO> items, BigDecimal subtotal,
                                       BigDecimal iva, BigDecimal total) {

        List<String> avisos = new ArrayList<>();
        BigDecimal suma = BigDecimal.ZERO;
        boolean sumaCompleta = true;

        for (int i = 0; i < items.size(); i++) {
            ItemCotizacionDTO it = items.get(i);
            if (it.cantidad() != null && it.valorUnitario() != null && it.valorBruto() != null
                    && difiere(it.cantidad().multiply(it.valorUnitario()), it.valorBruto())) {
                avisos.add("Material " + (i + 1) + " (" + it.descripcion() + "): cantidad x valor unitario = "
                        + pesos(it.cantidad().multiply(it.valorUnitario())) + ", pero el valor bruto es "
                        + pesos(it.valorBruto()));
            }
            if (it.valorBruto() != null) {
                suma = suma.add(it.valorBruto());
            } else {
                sumaCompleta = false;
            }
        }

        if (!items.isEmpty() && sumaCompleta && subtotal != null && difiere(suma, subtotal)) {
            avisos.add("La suma de los materiales (" + pesos(suma) + ") no coincide con el subtotal ("
                    + pesos(subtotal) + ")");
        }
        if (subtotal != null && iva != null && difiere(subtotal.multiply(TASA_IVA), iva)) {
            avisos.add("El IVA (" + pesos(iva) + ") no es el 19 % del subtotal ("
                    + pesos(subtotal.multiply(TASA_IVA)) + ")");
        }
        if (subtotal != null && iva != null && total != null && difiere(subtotal.add(iva), total)) {
            avisos.add("Subtotal + IVA (" + pesos(subtotal.add(iva)) + ") no coincide con el total ("
                    + pesos(total) + ")");
        }
        return avisos;
    }

    // --------------------------------------------------------------- utilidades

    private CotizacionDTO aDTO(Cotizacion c) {
        List<ItemCotizacionDTO> items = c.getItems().stream().map(ItemCotizacionDTO::desde).toList();
        return CotizacionDTO.desde(c, revisarValores(items, c.getSubtotal(), c.getIva(), c.getTotal()));
    }

    private Cotizacion buscarCotizacion(Long id) {
        return cotizacionRepositorio.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Cotización no encontrada"));
    }

    private static boolean difiere(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCIA) > 0;
    }

    private static String pesos(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        formato.setMinimumFractionDigits(0);
        formato.setMaximumFractionDigits(2);
        return "$" + formato.format(valor);
    }

    private static <T> T obligatorio(T valor, String campo) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            throw error(HttpStatus.BAD_REQUEST, "El campo '" + campo + "' es obligatorio");
        }
        return valor;
    }

    private static String texto(String valor, String campo, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw error(HttpStatus.BAD_REQUEST, "El campo '" + campo + "' admite máximo " + maximo + " caracteres");
        }
        return limpio;
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static BigDecimal noNegativo(BigDecimal valor, String campo) {
        if (valor.signum() < 0) {
            throw error(HttpStatus.BAD_REQUEST, "El campo '" + campo + "' no puede ser negativo");
        }
        return valor;
    }

    private static ResponseStatusException error(HttpStatus estado, String mensaje) {
        return new ResponseStatusException(estado, mensaje);
    }
}
