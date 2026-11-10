package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.ClienteDTO;
import com.ejemplo.demo.dto.ClienteRequest;
import com.ejemplo.demo.modelo.Cliente;
import com.ejemplo.demo.repositorio.ClienteRepositorio;
import com.ejemplo.demo.repositorio.SiigoCotizacionRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Catálogo de clientes (empresas). El NIT es la llave para cruzar con las cotizaciones de Siigo. */
@Service
public class ClienteServicio {

    static final String ENTIDAD = "CLIENTE";

    private final ClienteRepositorio clientes;
    private final SiigoCotizacionRepositorio cotizaciones;
    private final AuditoriaServicio auditoria;

    public ClienteServicio(ClienteRepositorio clientes, SiigoCotizacionRepositorio cotizaciones,
                           AuditoriaServicio auditoria) {
        this.clientes = clientes;
        this.cotizaciones = cotizaciones;
        this.auditoria = auditoria;
    }

    /** NIT de Siigo sin cliente creado, con cuántas cotizaciones tiene. */
    public record NitSinCliente(String nit, long cotizaciones) {
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listar() {
        Map<String, Long> conteo = conteoPorNit();
        return clientes.findAllByOrderByNombreAsc().stream()
                .map(c -> ClienteDTO.desde(c, conteo.getOrDefault(c.getNit(), 0L)))
                .toList();
    }

    /** Para saber qué clientes faltan por crear: NITs que aparecen en Siigo y no están en el catálogo. */
    @Transactional(readOnly = true)
    public List<NitSinCliente> nitsSinCliente() {
        Set<String> creados = clientes.findAll().stream().map(Cliente::getNit).collect(Collectors.toSet());
        return conteoPorNit().entrySet().stream()
                .filter(e -> !creados.contains(e.getKey()))
                .map(e -> new NitSinCliente(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(NitSinCliente::cotizaciones).reversed())
                .toList();
    }

    @Transactional
    public ClienteDTO crear(ClienteRequest r) {
        Cliente c = new Cliente();
        aplicar(c, r);
        clientes.save(c);
        auditoria.registrar("CREAR_CLIENTE", ENTIDAD, c.getId(), c.getNit() + " " + c.getNombre());
        return ClienteDTO.desde(c, conteoPorNit().getOrDefault(c.getNit(), 0L));
    }

    @Transactional
    public ClienteDTO actualizar(Long id, ClienteRequest r) {
        Cliente c = clientes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        aplicar(c, r);
        auditoria.registrar("EDITAR_CLIENTE", ENTIDAD, id, c.getNit() + " " + c.getNombre());
        return ClienteDTO.desde(c, conteoPorNit().getOrDefault(c.getNit(), 0L));
    }

    @Transactional
    public void eliminar(Long id) {
        Cliente c = clientes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        clientes.delete(c);
        auditoria.registrar("ELIMINAR_CLIENTE", ENTIDAD, id, c.getNit() + " " + c.getNombre());
    }

    private void aplicar(Cliente c, ClienteRequest r) {
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo de la petición es obligatorio");
        }
        String nit = Cliente.normalizarNit(r.nit());
        if (nit == null || nit.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El NIT es obligatorio (solo números)");
        }
        if (r.nombre() == null || r.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        if (r.nombre().strip().length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre admite máximo 200 caracteres");
        }
        clientes.findByNit(nit).filter(otro -> !otro.getId().equals(c.getId())).ifPresent(otro -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un cliente con el NIT " + nit + ": " + otro.getNombre());
        });
        c.setNit(nit);
        c.setNombre(r.nombre().strip());
        if (r.activo() != null) {
            c.setActivo(r.activo());
        }
    }

    private Map<String, Long> conteoPorNit() {
        Map<String, Long> conteo = new HashMap<>();
        for (Object[] fila : cotizaciones.contarPorNit()) {
            conteo.put((String) fila[0], ((Number) fila[1]).longValue());
        }
        return conteo;
    }
}
