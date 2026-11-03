package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.LoginRequest;
import com.ejemplo.demo.dto.LoginResponse;
import com.ejemplo.demo.dto.RegisterRequest;
import com.ejemplo.demo.dto.UsuarioDTO;
import com.ejemplo.demo.modelo.RefreshToken;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.RefreshTokenRepository;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.CargadorPermisos;
import com.ejemplo.demo.seguridad.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UsuarioRepositorio usuarioRepositorio;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final CargadorPermisos cargadorPermisos;
    private final AuditoriaServicio auditoria;

    public AuthService(
            UsuarioRepositorio usuarioRepositorio,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            CargadorPermisos cargadorPermisos,
            AuditoriaServicio auditoria) {

        this.usuarioRepositorio = usuarioRepositorio;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.cargadorPermisos = cargadorPermisos;
        this.auditoria = auditoria;
    }

    public LoginResponse login(LoginRequest request) {

        if (request.getEmail() == null || request.getPassword() == null) {
            throw new BadCredentialsException("Email o contraseña incorrectos");
        }

        // Mismo mensaje si el email no existe o la contraseña falla: no revela qué emails existen
        Usuario usuario = usuarioRepositorio.findByEmail(request.getEmail().trim().toLowerCase())
                .or(() -> usuarioRepositorio.findByEmail(request.getEmail()))
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Email o contraseña incorrectos"));

        if (!usuario.estaActivo()) {
            throw new DisabledException("El usuario está desactivado");
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

    /** Registro público: el usuario queda activo pero SIN rol hasta que un SUPERADMIN le asigne uno. */
    @Transactional
    public void registrar(RegisterRequest request) {

        if (request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email y contraseña son obligatorios");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (usuarioRepositorio.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario ya existe");
        }

        Usuario usuario = new Usuario();

        usuario.setNombre(request.getNombre());
        usuario.setEmail(email);
        usuario.setActivo(true);
        usuario.setRol(null);

        // Aquí se cifra la contraseña
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));

        usuarioRepositorio.save(usuario);

        auditoria.registrarComo(email, "REGISTRO", AuditoriaServicio.ENTIDAD_USUARIO,
                usuario.getId(), "Registro público, sin rol");
    }

    /** Datos del usuario autenticado, con su rol y permisos (para el frontend). */
    public UsuarioDTO usuarioActual(String email) {

        Usuario usuario = usuarioRepositorio.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        return UsuarioDTO.desde(usuario, cargadorPermisos.codigosDe(usuario));
    }
}
