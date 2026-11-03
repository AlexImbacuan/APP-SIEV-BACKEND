package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.CrearRolRequest;
import com.ejemplo.demo.dto.PermisoDTO;
import com.ejemplo.demo.dto.RolDTO;
import com.ejemplo.demo.modelo.Permiso;
import com.ejemplo.demo.modelo.Rol;
import com.ejemplo.demo.repositorio.PermisoRepositorio;
import com.ejemplo.demo.repositorio.RolRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ejemplo.demo.seguridad.Permisos.ROL_SUPERADMIN;
import static com.ejemplo.demo.servicio.AuditoriaServicio.ENTIDAD_ROL;

@Service
@Transactional
public class RolServicio {

    private final RolRepositorio rolRepositorio;
    private final PermisoRepositorio permisoRepositorio;
    private final AuditoriaServicio auditoria;

    public RolServicio(RolRepositorio rolRepositorio,
                       PermisoRepositorio permisoRepositorio,
                       AuditoriaServicio auditoria) {
        this.rolRepositorio = rolRepositorio;
        this.permisoRepositorio = permisoRepositorio;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<RolDTO> listarRoles() {
        return rolRepositorio.findAll().stream()
                .sorted(Comparator.comparing(Rol::getId))
                .map(RolDTO::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermisoDTO> listarPermisos() {
        return permisoRepositorio.findAll().stream()
                .sorted(Comparator.comparing(Permiso::getModulo).thenComparing(Permiso::getCodigo))
                .map(PermisoDTO::desde)
                .toList();
    }

    public RolDTO crearRol(CrearRolRequest request) {

        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo 'nombre' es obligatorio");
        }

        String nombre = request.nombre().trim().toUpperCase();

        if (rolRepositorio.findByNombre(nombre).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un rol con ese nombre");
        }

        Rol rol = new Rol(nombre, request.descripcion());
        rol.setPermisos(resolverPermisos(request.permisos()));
        rolRepositorio.save(rol);

        auditoria.registrar("CREAR_ROL", ENTIDAD_ROL, rol.getId(),
                "nombre=" + nombre + ", permisos=" + codigos(rol.getPermisos()));

        return RolDTO.desde(rol);
    }

    /** Reemplaza todos los permisos del rol por la lista recibida. */
    public RolDTO actualizarPermisos(Long rolId, List<String> codigosPermiso) {

        Rol rol = rolRepositorio.findById(rolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no encontrado"));

        if (ROL_SUPERADMIN.equals(rol.getNombre())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rol SUPERADMIN siempre tiene todos los permisos y no se puede modificar");
        }

        List<String> antes = codigos(rol.getPermisos());
        Set<Permiso> nuevos = resolverPermisos(codigosPermiso);

        rol.getPermisos().clear();
        rol.getPermisos().addAll(nuevos);

        auditoria.registrar("ACTUALIZAR_PERMISOS_ROL", ENTIDAD_ROL, rolId,
                "rol=" + rol.getNombre() + ", antes=" + antes + ", despues=" + codigos(nuevos));

        return RolDTO.desde(rol);
    }

    /** Convierte códigos en entidades; responde 400 si alguno no existe. */
    private Set<Permiso> resolverPermisos(List<String> codigos) {

        if (codigos == null || codigos.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> pedidos = codigos.stream().map(String::trim).collect(Collectors.toSet());
        Set<Permiso> encontrados = new HashSet<>(permisoRepositorio.findByCodigoIn(pedidos));

        if (encontrados.size() != pedidos.size()) {
            Set<String> existentes = encontrados.stream().map(Permiso::getCodigo).collect(Collectors.toSet());
            List<String> desconocidos = pedidos.stream().filter(c -> !existentes.contains(c)).sorted().toList();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permisos desconocidos: " + desconocidos);
        }

        return encontrados;
    }

    private static List<String> codigos(Set<Permiso> permisos) {
        return permisos.stream().map(Permiso::getCodigo).sorted().toList();
    }
}
