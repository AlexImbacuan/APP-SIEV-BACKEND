package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Registro de cada sincronización con Siigo (para saber cuándo fue la última y si falló). */
@Entity
@Table(name = "siigo_sincronizacion")
public class SincronizacionSiigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Qué se sincronizó, ej. "quotations". */
    @Column(nullable = false, length = 40)
    private String recurso;

    /** AUTOMATICA, AL_ARRANCAR o MANUAL (con el email de quien la pidió). */
    @Column(nullable = false, length = 150)
    private String origen;

    @Column(nullable = false)
    private LocalDateTime inicio;

    private LocalDateTime fin;

    private boolean exitosa;

    private int totalEnSiigo;
    private int nuevas;
    private int actualizadas;
    private int eliminadas;
    private double segundos;

    @Column(length = 2000)
    private String error;

    protected SincronizacionSiigo() {
    }

    public SincronizacionSiigo(String recurso, String origen) {
        this.recurso = recurso;
        this.origen = origen;
        this.inicio = LocalDateTime.now();
    }

    public void terminarBien(int totalEnSiigo, int nuevas, int actualizadas, int eliminadas) {
        this.totalEnSiigo = totalEnSiigo;
        this.nuevas = nuevas;
        this.actualizadas = actualizadas;
        this.eliminadas = eliminadas;
        this.exitosa = true;
        cerrar();
    }

    public void terminarMal(String error) {
        this.error = error == null ? "Error desconocido"
                : error.length() > 2000 ? error.substring(0, 2000) : error;
        this.exitosa = false;
        cerrar();
    }

    private void cerrar() {
        this.fin = LocalDateTime.now();
        this.segundos = java.time.Duration.between(inicio, fin).toMillis() / 1000.0;
    }

    public Long getId() {
        return id;
    }

    public String getRecurso() {
        return recurso;
    }

    public String getOrigen() {
        return origen;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFin() {
        return fin;
    }

    public boolean isExitosa() {
        return exitosa;
    }

    public int getTotalEnSiigo() {
        return totalEnSiigo;
    }

    public int getNuevas() {
        return nuevas;
    }

    public int getActualizadas() {
        return actualizadas;
    }

    public int getEliminadas() {
        return eliminadas;
    }

    public double getSegundos() {
        return segundos;
    }

    public String getError() {
        return error;
    }
}
