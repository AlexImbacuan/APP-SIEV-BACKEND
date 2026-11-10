package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Copia local de una cotización de Siigo. Siigo es la fuente de verdad: esta tabla
 * solo la escribe la sincronización (SiigoSincronizacionServicio), nunca el usuario.
 * Las cotizaciones que desaparecen de Siigo no se borran: se marcan eliminadaEnSiigo.
 */
@Entity
@Table(name = "siigo_cotizacion", indexes = {
        @Index(name = "idx_siigo_cot_numero", columnList = "numero"),
        @Index(name = "idx_siigo_cot_nit", columnList = "clienteNit"),
        @Index(name = "idx_siigo_cot_fecha", columnList = "fecha")
})
public class SiigoCotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(precision = 15, scale = 2)
    private BigDecimal total;

    private int cantidadItems;

    /** Arreglo items tal como lo devuelve Siigo. */
    @Column(columnDefinition = "TEXT")
    private String itemsJson;

    @Column(length = 1000)
    private String urlPublica;

    private LocalDateTime creadoEnSiigo;
    private LocalDateTime actualizadoEnSiigo;

    @Column(nullable = false)
    private boolean eliminadaEnSiigo = false;

    /** Última vez que la sincronización cambió esta fila. */
    @Column(nullable = false)
    private LocalDateTime sincronizadoEn;

    /**
     * Copia los datos de Siigo. Devuelve true si algo cambió (para contar las actualizadas
     * y no reescribir filas iguales).
     */
    public boolean aplicar(String numero, Integer consecutivo, LocalDate fecha, String clienteNit,
                           String clienteSiigoId, Long vendedorId, BigDecimal total, int cantidadItems,
                           String itemsJson, String urlPublica, LocalDateTime creadoEnSiigo,
                           LocalDateTime actualizadoEnSiigo) {
        boolean cambio = !Objects.equals(this.numero, numero)
                || !Objects.equals(this.consecutivo, consecutivo)
                || !Objects.equals(this.fecha, fecha)
                || !Objects.equals(this.clienteNit, clienteNit)
                || !Objects.equals(this.clienteSiigoId, clienteSiigoId)
                || !Objects.equals(this.vendedorId, vendedorId)
                || (this.total == null ? total != null : total == null || this.total.compareTo(total) != 0)
                || this.cantidadItems != cantidadItems
                || !Objects.equals(this.itemsJson, itemsJson)
                || !Objects.equals(this.urlPublica, urlPublica)
                || !Objects.equals(this.creadoEnSiigo, creadoEnSiigo)
                || !Objects.equals(this.actualizadoEnSiigo, actualizadoEnSiigo)
                || this.eliminadaEnSiigo;
        if (!cambio) {
            return false;
        }
        this.numero = numero;
        this.consecutivo = consecutivo;
        this.fecha = fecha;
        this.clienteNit = clienteNit;
        this.clienteSiigoId = clienteSiigoId;
        this.vendedorId = vendedorId;
        this.total = total;
        this.cantidadItems = cantidadItems;
        this.itemsJson = itemsJson;
        this.urlPublica = urlPublica;
        this.creadoEnSiigo = creadoEnSiigo;
        this.actualizadoEnSiigo = actualizadoEnSiigo;
        this.eliminadaEnSiigo = false;
        this.sincronizadoEn = LocalDateTime.now();
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
}
