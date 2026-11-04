package com.ejemplo.demo.configuracion;

import com.ejemplo.demo.seguridad.JwtFiltro;
import com.ejemplo.demo.seguridad.ManejadorSeguridadJson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity // habilita @PreAuthorize en los controladores
public class SeguridadConfiguracion {

    private final JwtFiltro jwtFiltro;
    private final ManejadorSeguridadJson manejadorSeguridadJson;

    public SeguridadConfiguracion(JwtFiltro jwtFiltro, ManejadorSeguridadJson manejadorSeguridadJson) {
        this.jwtFiltro = jwtFiltro;
        this.manejadorSeguridadJson = manejadorSeguridadJson;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults()) // usa el CorsConfigurationSource de CorsConfiguracion
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(manejadorSeguridadJson) // 401 en JSON
                        .accessDeniedHandler(manejadorSeguridadJson)      // 403 en JSON
                )
                .authorizeHttpRequests(auth -> auth
                        // Únicas rutas públicas (con y sin prefijo /api para no romper el frontend)
                        .requestMatchers(HttpMethod.POST,
                                "/auth/login", "/auth/register", "/auth/refresh", "/auth/logout",
                                "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
                        // Documentación de la API (Swagger UI y especificación OpenAPI)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Denegar por defecto: todo lo demás requiere token.
                        // El permiso concreto lo exige @PreAuthorize en cada endpoint.
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {

        return configuration.getAuthenticationManager();
    }
}
