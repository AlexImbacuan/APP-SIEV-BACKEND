package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.RefreshToken;
import com.ejemplo.demo.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUsuario(Usuario usuario);

    void deleteByToken(String token);

    void deleteByUsuario(Usuario usuario);

}