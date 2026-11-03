package com.ejemplo.demo.seguridad;

import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Valida el JWT y carga los permisos del usuario DESDE LA BD en cada petición.
 * Así, un cambio de rol/permisos o una desactivación se aplica en la siguiente
 * petición, sin esperar a que el token expire.
 */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepositorio usuarioRepositorio;
    private final CargadorPermisos cargadorPermisos;

    public JwtFiltro(JwtService jwtService,
                     UsuarioRepositorio usuarioRepositorio,
                     CargadorPermisos cargadorPermisos) {
        this.jwtService = jwtService;
        this.usuarioRepositorio = usuarioRepositorio;
        this.cargadorPermisos = cargadorPermisos;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {

            String token = header.substring(7);

            if (jwtService.validarToken(token)) {

                String email = jwtService.extraerEmail(token);

                // Si el usuario ya no existe o está desactivado, no se autentica (=> 401)
                usuarioRepositorio.findByEmail(email)
                        .filter(Usuario::estaActivo)
                        .ifPresent(usuario -> autenticar(usuario, request));
            }
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(Usuario usuario, HttpServletRequest request) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        usuario.getEmail(),
                        null,
                        cargadorPermisos.authoritiesDe(usuario)
                );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
