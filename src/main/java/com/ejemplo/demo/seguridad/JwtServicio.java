package com.ejemplo.demo.seguridad;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtServicio {

    private final String claveSecreta = "miClaveSecreta";
    private final long tiempoExpiracion = 86400000; // 1 día en milisegundos

    public String generarToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + tiempoExpiracion))
                .signWith(SignatureAlgorithm.HS256, claveSecreta.getBytes())
                .compact();
    }
}