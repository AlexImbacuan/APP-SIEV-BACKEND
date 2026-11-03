package com.ejemplo.demo.modelo;

import jakarta.persistence.*;

@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Column(unique = true)
    private String email;

    // Siempre guardado con BCrypt (ver PasswordEncoder en SeguridadConfiguracion)
    private String password;

    // Nullable: los usuarios existentes y los registrados quedan sin rol
    // hasta que un SUPERADMIN les asigne uno.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id")
    private Rol rol;

    // Nullable para que ddl-auto=update pueda agregar la columna a una tabla
    // con datos. null se trata como activo (ver estaActivo()).
    private Boolean activo;

    public Usuario() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public boolean estaActivo() {
        return activo == null || activo;
    }

    public boolean tieneRol(String nombreRol) {
        return rol != null && rol.getNombre().equals(nombreRol);
    }
}
