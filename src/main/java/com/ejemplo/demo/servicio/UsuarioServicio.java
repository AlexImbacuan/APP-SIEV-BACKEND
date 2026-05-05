package com.ejemplo.demo.servicio;

import java.util.List;
import com.ejemplo.demo.modelo.Usuario;

public interface UsuarioServicio {

    List<Usuario> listar();

    Usuario guardar(Usuario usuario);

}