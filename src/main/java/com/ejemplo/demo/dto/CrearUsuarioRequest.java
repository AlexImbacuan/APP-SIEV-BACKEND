package com.ejemplo.demo.dto;

/** rolId es opcional: sin él, el usuario queda sin rol. */
public record CrearUsuarioRequest(String nombre, String email, String password, Long rolId) {
}
