package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Registro de quién cambió usuarios, roles o permisos, qué cambió y cuándo.
 */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Email de quien hizo el cambio ("sistema" para el seed). */
    @Column(nullable = false)
    private String actor;

    /** Ej. CREAR_USUARIO, ASIGNAR_ROL, ACTUALIZAR_PERMISOS_ROL */
    @Column(nullable = false, length = 50)
    private String accion;

    /** USUARIO o ROL */
    @Column(nullable = false, length = 50)
    private String entidad;

    private String entidadId;

    @Column(length = 2000)
    private String detalle;

    public Auditoria() {
    }

    public Auditoria(String actor, String accion, String entidad, String entidadId, String detalle) {
        this.fecha = LocalDateTime.now();
        this.actor = actor;
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.detalle = detalle;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getActor() {
        return actor;
    }

    public String getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public String getEntidadId() {
        return entidadId;
    }

    public String getDetalle() {
        return detalle;
    }
}
