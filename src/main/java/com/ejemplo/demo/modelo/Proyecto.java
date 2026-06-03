package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

@Entity
public class Proyecto {

    @Id
    private String serial; // ID único

    @Column(nullable = false)
    private String descripcionGlobal;

    @Column(nullable = false)
    private Long valorTotal; // Pesos sin decimales

    @Column(nullable = false)
    private Integer factura;

    @Column(nullable = false)
    private Boolean pago; // OK (true/false)

    private String observacion;

    // Getters y Setters
    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getDescripcionGlobal() {
        return descripcionGlobal;
    }

    public void setDescripcionGlobal(String descripcionGlobal) {
        this.descripcionGlobal = descripcionGlobal;
    }

    public Long getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(Long valorTotal) {
        this.valorTotal = valorTotal;
    }

    public Integer getFactura() {
        return factura;
    }

    public void setFactura(Integer factura) {
        this.factura = factura;
    }

    public Boolean getPago() {
        return pago;
    }

    public void setPago(Boolean pago) {
        this.pago = pago;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}