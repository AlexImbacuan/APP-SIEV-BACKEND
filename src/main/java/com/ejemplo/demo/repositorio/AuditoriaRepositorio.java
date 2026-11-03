package com.ejemplo.demo.repositorio;

import com.ejemplo.demo.modelo.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaRepositorio extends JpaRepository<Auditoria, Long>, JpaSpecificationExecutor<Auditoria> {

}
