package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.SincronizacionSiigo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SincronizacionSiigoRepositorio extends JpaRepository<SincronizacionSiigo, Long> {

    Optional<SincronizacionSiigo> findFirstByRecursoOrderByInicioDesc(String recurso);

    Optional<SincronizacionSiigo> findFirstByRecursoAndExitosaTrueOrderByInicioDesc(String recurso);

    List<SincronizacionSiigo> findTop20ByRecursoOrderByInicioDesc(String recurso);
}
