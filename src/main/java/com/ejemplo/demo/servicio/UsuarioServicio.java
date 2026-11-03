package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.ActualizarUsuarioRequest;
import com.ejemplo.demo.dto.CrearUsuarioRequest;
import com.ejemplo.demo.dto.UsuarioDTO;

import java.util.List;

public interface UsuarioServicio {

    List<UsuarioDTO> listar();

    UsuarioDTO obtener(Long id);

    UsuarioDTO crear(CrearUsuarioRequest request);

    UsuarioDTO actualizar(Long id, ActualizarUsuarioRequest request);

    UsuarioDTO cambiarEstado(Long id, boolean activo);

    UsuarioDTO asignarRol(Long id, Long rolId);

}
