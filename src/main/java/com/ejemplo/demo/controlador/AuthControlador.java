package com.ejemplo.demo.controlador;

import com.ejemplo.demo.seguridad.JwtServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthControlador {

    private final JwtServicio jwtServicio;

    public AuthControlador(JwtServicio jwtServicio) {
        this.jwtServicio = jwtServicio;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username) {
        String token = jwtServicio.generarToken(username);
        return ResponseEntity.ok(token);
    }
}