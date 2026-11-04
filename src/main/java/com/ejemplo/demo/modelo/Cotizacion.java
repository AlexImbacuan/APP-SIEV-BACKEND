package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cotización enviada a un cliente. Se crea a mano o leyendo el PDF exportado de Siigo
 * (el PDF nunca se guarda). Valores en pesos con 2 decimales.
 */
@Entity
@Table(name = "cotizacion")
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de Siigo, ej. "C-1-2562". Único. */
    @Column(nullable = false, unique = true, length = 50)
    private String numero;

    @Column(nullable = false)
    private String cliente;

    private String ingSolicitante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCotizacion estado = EstadoCotizacion.PENDIENTE;

    /** Solo cuando el estado es NO_APROBADA. */
    @Column(length = 1000)
    private String motivoRechazo;

    /** Responsable SIEV: un usuario del sistema. */
    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private Usuario responsable;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicion ASC")
    private List<ItemCotizacion> items = new ArrayList<>();

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal iva;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /**
     * Número de la factura con la que se facturó (texto libre por ahora).
     * Cuando exista el módulo de Facturación pasará a ser una relación con la factura.
     */
    @Column(length = 50)
    private String numeroFactura;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = LocalDateTime.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = LocalDateTime.now();
    }

    /** Reemplaza todos los materiales manteniendo el orden recibido. */
    public void reemplazarItems(List<ItemCotizacion> nuevos) {
        items.clear();
        int posicion = 1;
        for (ItemCotizacion item : nuevos) {
            item.setCotizacion(this);
            item.setPosicion(posicion++);
            items.add(item);
        }
    }

    public boolean estaFacturada() {
        return numeroFactura != null && !numeroFactura.isBlank();
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public String getIngSolicitante() {
        return ingSolicitante;
    }

    public void setIngSolicitante(String ingSolicitante) {
        this.ingSolicitante = ingSolicitante;
    }

    public EstadoCotizacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoCotizacion estado) {
        this.estado = estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Usuario getResponsable() {
        return responsable;
    }

    public void setResponsable(Usuario responsable) {
        this.responsable = responsable;
    }

    public List<ItemCotizacion> getItems() {
        return items;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getIva() {
        return iva;
    }

    public void setIva(BigDecimal iva) {
        this.iva = iva;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public void setNumeroFactura(String numeroFactura) {
        this.numeroFactura = numeroFactura;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
