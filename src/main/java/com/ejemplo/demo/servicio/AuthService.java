package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.LoginRequest;
import com.ejemplo.demo.dto.LoginResponse;
import com.ejemplo.demo.dto.RegisterRequest;
import com.ejemplo.demo.modelo.RefreshToken;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.RefreshTokenRepository;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UsuarioRepositorio usuarioRepositorio;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UsuarioRepositorio usuarioRepositorio,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {

        this.usuarioRepositorio = usuarioRepositorio;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {

        Usuario usuario = usuarioRepositorio.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        String accessToken = jwtService.generarToken(usuario);
        String nuevoRefreshToken = jwtService.generarRefreshToken();

        // Buscar si el usuario ya tiene un refresh token
        RefreshToken refresh = refreshTokenRepository
                .findByUsuario(usuario)
                .orElse(new RefreshToken());

        // Actualizar o crear
        refresh.setUsuario(usuario);
        refresh.setToken(nuevoRefreshToken);
        refresh.setExpiracion(LocalDateTime.now().plusDays(7));

        refreshTokenRepository.save(refresh);

        return new LoginResponse(accessToken, nuevoRefreshToken);
    }

    public void registrar(RegisterRequest request) {

        if (usuarioRepositorio.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("El usuario ya existe");
        }

        Usuario usuario = new Usuario();

        usuario.setNombre(request.getNombre());
        usuario.setEmail(request.getEmail());

        // Aquí se cifra la contraseña
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));

        usuarioRepositorio.save(usuario);
    }

}