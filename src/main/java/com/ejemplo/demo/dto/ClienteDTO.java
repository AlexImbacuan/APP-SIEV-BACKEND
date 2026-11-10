package com.ejemplo.demo.dto;

import com.ejemplo.demo.modelo.Cliente;

import java.time.LocalDateTime;

public record ClienteDTO(Long id, String nit, String nombre, boolean activo, LocalDateTime creadoEn,
                         long cotizaciones) {

    public static ClienteDTO desde(Cliente c, long cotizaciones) {
        return new ClienteDTO(c.getId(), c.getNit(), c.getNombre(), c.isActivo(), c.getCreadoEn(), cotizaciones);
    }
}
