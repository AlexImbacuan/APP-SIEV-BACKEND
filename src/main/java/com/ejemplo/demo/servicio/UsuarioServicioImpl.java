package com.ejemplo.demo.servicio;

import com.ejemplo.demo.dto.ActualizarUsuarioRequest;
import com.ejemplo.demo.dto.CrearUsuarioRequest;
import com.ejemplo.demo.dto.UsuarioDTO;
import com.ejemplo.demo.modelo.Rol;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.RolRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.ejemplo.demo.seguridad.CargadorPermisos;
import com.ejemplo.demo.seguridad.UsuarioActual;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static com.ejemplo.demo.seguridad.Permisos.ROL_SUPERADMIN;
import static com.ejemplo.demo.servicio.AuditoriaServicio.ENTIDAD_USUARIO;

@Service
@Transactional
public class UsuarioServicioImpl implements UsuarioServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final RolRepositorio rolRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final CargadorPermisos cargadorPermisos;
    private final AuditoriaServicio auditoria;

    public UsuarioServicioImpl(UsuarioRepositorio usuarioRepositorio,
                               RolRepositorio rolRepositorio,
                               PasswordEncoder passwordEncoder,
                               CargadorPermisos cargadorPermisos,
                               AuditoriaServicio auditoria) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.rolRepositorio = rolRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.cargadorPermisos = cargadorPermisos;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDTO> listar() {
        return usuarioRepositorio.findAll().stream().map(this::aDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDTO obtener(Long id) {
        return aDTO(buscar(id));
    }

    @Override
    public UsuarioDTO crear(CrearUsuarioRequest request) {

        String email = obligatorio(request.email(), "email").toLowerCase();
        String nombre = obligatorio(request.nombre(), "nombre");
        String password = obligatorio(request.password(), "password");

        if (usuarioRepositorio.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese email");
        }

        Rol rol = request.rolId() != null ? buscarRol(request.rolId()) : null;
        exigirSuperadminSiRolEs(rol);

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuarioRepositorio.save(usuario);

        auditoria.registrar("CREAR_USUARIO", ENTIDAD_USUARIO, usuario.getId(),
                "email=" + email + ", rol=" + nombreRol(rol));

        return aDTO(usuario);
    }

    @Override
    public UsuarioDTO actualizar(Long id, ActualizarUsuarioRequest request) {

        Usuario usuario = buscar(id);
        exigirSuperadminSiObjetivoEsSuperadmin(usuario);

        List<String> cambios = new ArrayList<>();

        if (tieneTexto(request.nombre()) && !request.nombre().equals(usuario.getNombre())) {
            cambios.add("nombre: " + usuario.getNombre() + " -> " + request.nombre());
            usuario.setNombre(request.nombre().trim());
        }

        if (tieneTexto(request.email())) {
            String email = request.email().trim().toLowerCase();
            if (!email.equals(usuario.getEmail())) {
                if (usuarioRepositorio.existsByEmail(email)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese email");
                }
                cambios.add("email: " + usuario.getEmail() + " -> " + email);
                usuario.setEmail(email);
            }
        }

        if (tieneTexto(request.password())) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
            cambios.add("password cambiado"); // nunca se guarda el valor
        }

        if (!cambios.isEmpty()) {
            auditoria.registrar("EDITAR_USUARIO", ENTIDAD_USUARIO, id, String.join("; ", cambios));
        }

        return aDTO(usuario);
    }

    @Override
    public UsuarioDTO cambiarEstado(Long id, boolean activo) {

        Usuario usuario = buscar(id);
        exigirSuperadminSiObjetivoEsSuperadmin(usuario);

        if (!activo) {
            if (usuario.getEmail().equals(UsuarioActual.email())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes desactivarte a ti mismo");
            }
            protegerUltimoSuperadmin(usuario);
        }

        if (usuario.estaActivo() != activo) {
            usuario.setActivo(activo);
            auditoria.registrar(activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO",
                    ENTIDAD_USUARIO, id, "email=" + usuario.getEmail());
        }

        return aDTO(usuario);
    }

    @Override
    public UsuarioDTO asignarRol(Long id, Long rolId) {

        Usuario usuario = buscar(id);
        Rol nuevoRol = rolId != null ? buscarRol(rolId) : null;

        exigirSuperadminSiObjetivoEsSuperadmin(usuario);
        exigirSuperadminSiRolEs(nuevoRol);

        boolean pierdeSuperadmin = usuario.tieneRol(ROL_SUPERADMIN)
                && (nuevoRol == null || !ROL_SUPERADMIN.equals(nuevoRol.getNombre()));

        if (pierdeSuperadmin) {
            if (usuario.getEmail().equals(UsuarioActual.email())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No puedes quitarte a ti mismo el rol SUPERADMIN");
            }
            protegerUltimoSuperadmin(usuario);
        }

        String rolAnterior = nombreRol(usuario.getRol());
        usuario.setRol(nuevoRol);

        auditoria.registrar("ASIGNAR_ROL", ENTIDAD_USUARIO, id,
                "email=" + usuario.getEmail() + ", rol: " + rolAnterior + " -> " + nombreRol(nuevoRol));

        return aDTO(usuario);
    }

    // ------------------------------------------------------------------ reglas

    /** Impide dejar el sistema sin ningún SUPERADMIN activo. */
    private void protegerUltimoSuperadmin(Usuario usuario) {
        if (usuario.tieneRol(ROL_SUPERADMIN) && usuario.estaActivo()
                && usuarioRepositorio.contarActivosConRol(ROL_SUPERADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede quitar ni desactivar al último SUPERADMIN");
        }
    }

    /** Solo un SUPERADMIN puede modificar a otro SUPERADMIN. */
    private void exigirSuperadminSiObjetivoEsSuperadmin(Usuario objetivo) {
        if (objetivo.tieneRol(ROL_SUPERADMIN) && !actorEsSuperadmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo un SUPERADMIN puede modificar a otro SUPERADMIN");
        }
    }

    /** Solo un SUPERADMIN puede otorgar el rol SUPERADMIN (evita escalar privilegios). */
    private void exigirSuperadminSiRolEs(Rol rol) {
        if (rol != null && ROL_SUPERADMIN.equals(rol.getNombre()) && !actorEsSuperadmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo un SUPERADMIN puede asignar el rol SUPERADMIN");
        }
    }

    private boolean actorEsSuperadmin() {
        String email = UsuarioActual.email();
        return email != null && usuarioRepositorio.findByEmail(email)
                .map(u -> u.tieneRol(ROL_SUPERADMIN))
                .orElse(false);
    }

    // --------------------------------------------------------------- utilidades

    private Usuario buscar(Long id) {
        return usuarioRepositorio.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private Rol buscarRol(Long rolId) {
        return rolRepositorio.findById(rolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol indicado no existe"));
    }

    private UsuarioDTO aDTO(Usuario usuario) {
        return UsuarioDTO.desde(usuario, cargadorPermisos.codigosDe(usuario));
    }

    private static String nombreRol(Rol rol) {
        return rol != null ? rol.getNombre() : "(sin rol)";
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String obligatorio(String valor, String campo) {
        if (!tieneTexto(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo '" + campo + "' es obligatorio");
        }
        return valor.trim();
    }
}
