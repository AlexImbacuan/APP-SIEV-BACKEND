package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

/**
 * Permiso con formato "modulo:accion" (ej. proyectos:ver).
 * El código es lo que Spring Security usa como authority.
 */
@Entity
@Table(name = "permiso")
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String codigo;

    private String descripcion;

    @Column(nullable = false, length = 50)
    private String modulo;

    public Permiso() {
    }

    public Permiso(String codigo, String descripcion, String modulo) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.modulo = modulo;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
    }
}
