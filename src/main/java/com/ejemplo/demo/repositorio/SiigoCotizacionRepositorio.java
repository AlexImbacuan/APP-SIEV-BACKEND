package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.SiigoCotizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SiigoCotizacionRepositorio extends JpaRepository<SiigoCotizacion, Long>,
        JpaSpecificationExecutor<SiigoCotizacion> {
}
