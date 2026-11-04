package com.ejemplo.demo;

import com.ejemplo.demo.modelo.RefreshToken;
import com.ejemplo.demo.modelo.Usuario;
import com.ejemplo.demo.repositorio.RefreshTokenRepository;
import com.ejemplo.demo.repositorio.RolRepositorio;
import com.ejemplo.demo.repositorio.UsuarioRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /auth/refresh y POST /auth/logout. */
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenTests {

    private static final String PASSWORD = "Clave-123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private RolRepositorio rolRepositorio;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void refresh_devuelveTokensNuevosQueFuncionan() throws Exception {
        JsonNode login = login(crearUsuario("refresh-ok@test.com"));

        JsonNode nuevos = leer(refresh(login.get("refreshToken").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty()));

        assertNotEquals(login.get("refreshToken").asText(), nuevos.get("refreshToken").asText());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + nuevos.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("refresh-ok@test.com"));
    }

    @Test
    void refresh_rotaElToken_elAnteriorYaNoSirve() throws Exception {
        String anterior = login(crearUsuario("refresh-rota@test.com")).get("refreshToken").asText();

        refresh(anterior).andExpect(status().isOk());
        refresh(anterior)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401));
    }

    @Test
    void refresh_tokenInexistenteOVacio_recibe401() throws Exception {
        refresh("no-existe").andExpect(status().isUnauthorized());
        refresh("").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_tokenExpirado_recibe401() throws Exception {
        String email = "refresh-expirado@test.com";
        String token = login(crearUsuario(email)).get("refreshToken").asText();

        RefreshToken guardado = refreshTokenRepository.findByToken(token).orElseThrow();
        guardado.setExpiracion(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(guardado);

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_usuarioDesactivado_recibe401() throws Exception {
        Usuario usuario = crearUsuario("refresh-inactivo@test.com");
        String token = login(usuario).get("refreshToken").asText();

        usuario.setActivo(false);
        usuarioRepositorio.save(usuario);

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_invalidaElRefreshToken() throws Exception {
        String token = login(crearUsuario("logout@test.com")).get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(token)))
                .andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_conTokenInexistente_noFalla() throws Exception {
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("no-existe")))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ utilidades

    private Usuario crearUsuario(String email) {
        Usuario u = new Usuario();
        u.setNombre(email);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(PASSWORD));
        u.setActivo(true);
        u.setRol(rolRepositorio.findByNombre("TECNICO").orElseThrow());
        return usuarioRepositorio.save(u);
    }

    private JsonNode login(Usuario usuario) throws Exception {
        return leer(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + usuario.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(refreshToken)));
    }

    private String cuerpo(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
