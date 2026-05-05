package com.ejemplo.demo.servicio;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;

@Service
public class UsuarioServicioImpl implements UsuarioServicio {

    private final UsuarioRepositorio repositorio;

    public UsuarioServicioImpl(UsuarioRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<Usuario> listar() {
        return repositorio.findAll();
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        return repositorio.save(usuario);
    }
}