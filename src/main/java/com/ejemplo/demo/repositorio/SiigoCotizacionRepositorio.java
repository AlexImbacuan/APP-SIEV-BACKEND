package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.SiigoCotizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SiigoCotizacionRepositorio extends JpaRepository<SiigoCotizacion, Long>,
        JpaSpecificationExecutor<SiigoCotizacion> {

    /** [nit, cantidad] de las cotizaciones vigentes, agrupadas por NIT. */
    @Query("select c.clienteNit, count(c) from SiigoCotizacion c "
            + "where c.eliminadaEnSiigo = false and c.clienteNit is not null group by c.clienteNit")
    List<Object[]> contarPorNit();
}
