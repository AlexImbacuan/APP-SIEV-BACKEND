package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNit(String nit);

    List<Cliente> findByNitIn(Collection<String> nits);

    List<Cliente> findAllByOrderByNombreAsc();
}
