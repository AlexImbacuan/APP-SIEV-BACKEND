package com.ejemplo.demo.controlador;

import com.ejemplo.demo.dto.LoginRequest;
import com.ejemplo.demo.dto.LoginResponse;
import com.ejemplo.demo.dto.RegisterRequest;
import com.ejemplo.demo.servicio.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
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

}