package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cotización de Siigo + control SIEV.
 *
 * Dos grupos de campos:
 *  - DATOS DE SIIGO: solo los escribe la sincronización (aplicar). Siigo es la fuente de verdad.
 *  - CONTROL SIEV: solo los escribe el usuario (con siigo:editar). La sincronización nunca los toca.
 *
 * descripcion e ingenieroSolicitante tienen las dos versiones: la leída del encabezado de Siigo
 * (descripcionSiigo / ingenieroSiigo) y la que escribe el usuario. Se muestra la del usuario si
 * existe; si no, la de Siigo.
 *
 * Las cotizaciones que desaparecen de Siigo no se borran: se marcan eliminadaEnSiigo.
 */
@Entity
@Table(name = "siigo_cotizacion", indexes = {
        @Index(name = "idx_siigo_cot_numero", columnList = "numero"),
        @Index(name = "idx_siigo_cot_nit", columnList = "clienteNit"),
        @Index(name = "idx_siigo_cot_fecha", columnList = "fecha"),
        @Index(name = "idx_siigo_cot_estado", columnList = "estado")
})
public class SiigoCotizacion {

    /** Datos que vienen de Siigo, ya normalizados. Se comparan con equals para detectar cambios. */
    public record DatosSiigo(String numero, Integer consecutivo, LocalDate fecha, String clienteNit,
                             String clienteSiigoId, Long vendedorId, BigDecimal subtotal, BigDecimal iva,
                             BigDecimal total, int cantidadItems, String itemsJson, String urlPublica,
                             String encabezado, String descripcion, String ingeniero, String sede,
                             LocalDateTime creadoEnSiigo, LocalDateTime actualizadoEnSiigo) {
        public DatosSiigo {
            subtotal = dinero(subtotal);
            iva = dinero(iva);
            total = dinero(total);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------------------------------------ DATOS DE SIIGO

    /** id (UUID) de la cotización en Siigo. */
    @Column(nullable = false, unique = true, length = 64)
    private String siigoId;

    /** Ej. "C-1-2857" (campo name de Siigo). */
    @Column(nullable = false, length = 50)
    private String numero;

    /** Ej. 2857 (campo number de Siigo). */
    private Integer consecutivo;

    private LocalDate fecha;

    @Column(length = 30)
    private String clienteNit;

    @Column(length = 64)
    private String clienteSiigoId;

    /** id del vendedor en Siigo (campo seller). */
    private Long vendedorId;

    /** Suma de cantidad × precio − descuento de los ítems. */
    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    /** Suma de los impuestos de los ítems. */
    @Column(precision = 15, scale = 2)
    private BigDecimal iva;

    @Column(precision = 15, scale = 2)
    private BigDecimal total;

    private int cantidadItems;

    /** Arreglo items tal como lo devuelve Siigo. */
    @Column(columnDefinition = "TEXT")
    private String itemsJson;

    @Column(length = 1000)
    private String urlPublica;

    /** Texto del encabezado/observaciones de Siigo, tal cual. */
    @Column(columnDefinition = "TEXT")
    private String encabezadoSiigo;

    /** Leídos del encabezado (ver EncabezadoCotizacion). */
    @Column(length = 500)
    private String descripcionSiigo;
    @Column(length = 150)
    private String ingenieroSiigo;
    @Column(length = 200)
    private String sedeSiigo;

    private LocalDateTime creadoEnSiigo;
    private LocalDateTime actualizadoEnSiigo;

    @Column(nullable = false)
    private boolean eliminadaEnSiigo = false;

    /** Última vez que la sincronización cambió esta fila. */
    @Column(nullable = false)
    private LocalDateTime sincronizadoEn;

    // ------------------------------------------------------------ CONTROL SIEV

    /** Escrita por el usuario; si es null se muestra descripcionSiigo. */
    @Column(length = 500)
    private String descripcion;

    /** Escrito por el usuario; si es null se muestra ingenieroSiigo. */
    @Column(length = 150)
    private String ingenieroSolicitante;

    /** Nullable en BD para no fallar al agregar la columna a filas existentes; ver getEstado(). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoSiigoCotizacion estado;

    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private Usuario responsable;

    @Column(length = 50)
    private String ordenCompra;

    @Column(precision = 15, scale = 2)
    private BigDecimal valorOc;

    /** N° de factura (texto libre por ahora; luego se cruzará con las facturas de Siigo). */
    @Column(length = 50)
    private String numeroFactura;

    private LocalDate fechaFactura;
    private LocalDate fechaPago;

    @Column(length = 100)
    private String comprobantePago;

    @Column(precision = 15, scale = 2)
    private BigDecimal valorPagado;

    private LocalDateTime controlActualizadoEn;

    @Column(length = 150)
    private String controlActualizadoPor;

    // ------------------------------------------------------------ sincronización

    public DatosSiigo datosSiigo() {
        return new DatosSiigo(numero, consecutivo, fecha, clienteNit, clienteSiigoId, vendedorId, subtotal, iva,
                total, cantidadItems, itemsJson, urlPublica, encabezadoSiigo, descripcionSiigo, ingenieroSiigo,
                sedeSiigo, creadoEnSiigo, actualizadoEnSiigo);
    }

    /** Copia los datos de Siigo. Devuelve true si algo cambió. No toca el control SIEV. */
    public boolean aplicar(DatosSiigo d) {
        boolean cambio = eliminadaEnSiigo || !d.equals(datosSiigo());
        if (estado == null) {
            estado = EstadoSiigoCotizacion.POR_APROBAR;
        }
        if (!cambio) {
            return false;
        }
        numero = d.numero();
        consecutivo = d.consecutivo();
        fecha = d.fecha();
        clienteNit = d.clienteNit();
        clienteSiigoId = d.clienteSiigoId();
        vendedorId = d.vendedorId();
        subtotal = d.subtotal();
        iva = d.iva();
        total = d.total();
        cantidadItems = d.cantidadItems();
        itemsJson = d.itemsJson();
        urlPublica = d.urlPublica();
        encabezadoSiigo = d.encabezado();
        descripcionSiigo = d.descripcion();
        ingenieroSiigo = d.ingeniero();
        sedeSiigo = d.sede();
        creadoEnSiigo = d.creadoEnSiigo();
        actualizadoEnSiigo = d.actualizadoEnSiigo();
        eliminadaEnSiigo = false;
        sincronizadoEn = LocalDateTime.now();
        return true;
    }

    /** Devuelve true si cambió (no estaba marcada). */
    public boolean marcarEliminada() {
        if (eliminadaEnSiigo) {
            return false;
        }
        eliminadaEnSiigo = true;
        sincronizadoEn = LocalDateTime.now();
        return true;
    }

    // ------------------------------------------------------------ control

    public void actualizarControl(String descripcion, String ingenieroSolicitante, Usuario responsable,
                                  String ordenCompra, BigDecimal valorOc, String numeroFactura,
                                  LocalDate fechaFactura, LocalDate fechaPago, String comprobantePago,
                                  BigDecimal valorPagado, String actor) {
        this.descripcion = descripcion;
        this.ingenieroSolicitante = ingenieroSolicitante;
        this.responsable = responsable;
        this.ordenCompra = ordenCompra;
        this.valorOc = dinero(valorOc);
        this.numeroFactura = numeroFactura;
        this.fechaFactura = fechaFactura;
        this.fechaPago = fechaPago;
        this.comprobantePago = comprobantePago;
        this.valorPagado = dinero(valorPagado);
        marcarControl(actor);
    }

    public void cambiarEstado(EstadoSiigoCotizacion nuevo, String actor) {
        this.estado = nuevo;
        marcarControl(actor);
    }

    private void marcarControl(String actor) {
        controlActualizadoEn = LocalDateTime.now();
        controlActualizadoPor = actor;
    }

    static BigDecimal dinero(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------ getters

    public Long getId() {
        return id;
    }

    public String getSiigoId() {
        return siigoId;
    }

    public void setSiigoId(String siigoId) {
        this.siigoId = siigoId;
    }

    public String getNumero() {
        return numero;
    }

    public Integer getConsecutivo() {
        return consecutivo;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getClienteNit() {
        return clienteNit;
    }

    public String getClienteSiigoId() {
        return clienteSiigoId;
    }

    public Long getVendedorId() {
        return vendedorId;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getIva() {
        return iva;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public int getCantidadItems() {
        return cantidadItems;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public String getUrlPublica() {
        return urlPublica;
    }

    public String getEncabezadoSiigo() {
        return encabezadoSiigo;
    }

    public String getDescripcionSiigo() {
        return descripcionSiigo;
    }

    public String getIngenieroSiigo() {
        return ingenieroSiigo;
    }

    public String getSedeSiigo() {
        return sedeSiigo;
    }

    public LocalDateTime getCreadoEnSiigo() {
        return creadoEnSiigo;
    }

    public LocalDateTime getActualizadoEnSiigo() {
        return actualizadoEnSiigo;
    }

    public boolean isEliminadaEnSiigo() {
        return eliminadaEnSiigo;
    }

    public LocalDateTime getSincronizadoEn() {
        return sincronizadoEn;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getIngenieroSolicitante() {
        return ingenieroSolicitante;
    }

    public EstadoSiigoCotizacion getEstado() {
        return estado == null ? EstadoSiigoCotizacion.POR_APROBAR : estado;
    }

    public Usuario getResponsable() {
        return responsable;
    }

    public String getOrdenCompra() {
        return ordenCompra;
    }

    public BigDecimal getValorOc() {
        return valorOc;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public LocalDate getFechaFactura() {
        return fechaFactura;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public String getComprobantePago() {
        return comprobantePago;
    }

    public BigDecimal getValorPagado() {
        return valorPagado;
    }

    public LocalDateTime getControlActualizadoEn() {
        return controlActualizadoEn;
    }

    public String getControlActualizadoPor() {
        return controlActualizadoPor;
    }
}
