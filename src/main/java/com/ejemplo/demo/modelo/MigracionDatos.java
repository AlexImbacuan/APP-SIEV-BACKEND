package com.ejemplo.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro de los cambios de datos que se aplican una sola vez al arrancar
 * (por ejemplo, ajustar los permisos de un rol que ya existe en la BD).
 * Si el código está aquí, la migración no se vuelve a ejecutar, así no pisa
 * los cambios que el SUPERADMIN haga después a mano.
 */
@Entity
@Table(name = "migracion_datos")
public class MigracionDatos {

    @Id
    @Column(length = 100)
    private String codigo;

    @Column(nullable = false)
    private LocalDateTime aplicadaEn;

    protected MigracionDatos() {
    }

    public MigracionDatos(String codigo) {
        this.codigo = codigo;
        this.aplicadaEn = LocalDateTime.now();
    }

    public String getCodigo() {
        return codigo;
    }

    public LocalDateTime getAplicadaEn() {
        return aplicadaEn;
    }
}
