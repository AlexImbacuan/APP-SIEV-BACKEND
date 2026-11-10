package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Cliente (empresa). Se cruza con las cotizaciones de Siigo por NIT para mostrar el nombre,
 * porque Siigo solo entrega el NIT. El NIT se guarda solo con dígitos y sin dígito de verificación.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String nit;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = LocalDateTime.now();
    }

    /** "800.197.463-1" -> "800197463" (igual que el NIT que entrega Siigo). */
    public static String normalizarNit(String nit) {
        if (nit == null) {
            return null;
        }
        String base = nit.contains("-") ? nit.substring(0, nit.indexOf('-')) : nit;
        String digitos = base.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    public Long getId() {
        return id;
    }

    public String getNit() {
        return nit;
    }

    public void setNit(String nit) {
        this.nit = nit;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
