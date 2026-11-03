package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Cuenta los usuarios activos (activo = true o null) que tienen el rol indicado. */
    @Query("select count(u) from Usuario u where u.rol.nombre = :rol and (u.activo is null or u.activo = true)")
    long contarActivosConRol(@Param("rol") String rol);

}
