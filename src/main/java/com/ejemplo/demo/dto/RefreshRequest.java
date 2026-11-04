package com.ejemplo.demo.dto;

/** Cuerpo de /auth/refresh y /auth/logout: { "refreshToken": "..." } */
public record RefreshRequest(String refreshToken) {
}
