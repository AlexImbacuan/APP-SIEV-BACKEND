package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.LoginRequest;
import com.ejemplo.demo.dto.LoginResponse;
import com.ejemplo.demo.dto.RegisterRequest;
import com.ejemplo.demo.dto.UsuarioDTO;
import com.ejemplo.demo.servicio.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Responde en /auth/* (frontend actual) y en /api/auth/* (ruta nueva). */
@RestController
@RequestMapping({"/auth", "/api/auth"})
public class AuthControlador {

    private final AuthService authService;

    public AuthControlador(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {

        authService.registrar(request);

        return ResponseEntity.ok("Usuario registrado correctamente");
    }

    /** Usuario actual con su rol y su lista de permisos. Solo requiere estar autenticado. */
    @GetMapping("/me")
    public UsuarioDTO me(Authentication authentication) {
        return authService.usuarioActual(authentication.getName());
    }

}
