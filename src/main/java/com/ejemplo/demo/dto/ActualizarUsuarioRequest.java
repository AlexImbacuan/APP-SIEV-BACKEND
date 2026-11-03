package com.ejemplo.demo.dto;

/** Todos los campos son opcionales: solo se cambian los que vienen con valor. */
public record ActualizarUsuarioRequest(String nombre, String email, String password) {
}
