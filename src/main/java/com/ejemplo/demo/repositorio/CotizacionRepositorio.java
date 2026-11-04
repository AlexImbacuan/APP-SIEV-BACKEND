package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.Cotizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CotizacionRepositorio extends JpaRepository<Cotizacion, Long>, JpaSpecificationExecutor<Cotizacion> {

    Optional<Cotizacion> findByNumeroIgnoreCase(String numero);
}
