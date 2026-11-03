package com.ejemplo.demo.configuracion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class VerificadorVariablesEntornoTests {

    private final VerificadorVariablesEntorno verificador = new VerificadorVariablesEntorno();

    private static final String SECRETO_VALIDO = "0123456789abcdefghijklmnopqrstuvwxyz";

    @Test
    void sinVariables_fallaListandoTodas() {
        MockEnvironment env = new MockEnvironment();

        VariablesEntornoFaltantesException ex =
                assertThrows(VariablesEntornoFaltantesException.class, () -> verificador.verificar(env));

        assertEquals(2, ex.getProblemas().size());
        assertTrue(ex.getProblemas().contains("Falta la variable de entorno SPRING_DATASOURCE_PASSWORD"));
        assertTrue(ex.getProblemas().contains("Falta la variable de entorno JWT_SECRET"));
    }

    @Test
    void placeholderSinResolver_cuentaComoFaltante() {
        // Así llega cuando application.properties dice ${SPRING_DATASOURCE_PASSWORD} y la variable no existe
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "${SPRING_DATASOURCE_PASSWORD}")
                .withProperty("jwt.secret", SECRETO_VALIDO);

        VariablesEntornoFaltantesException ex =
                assertThrows(VariablesEntornoFaltantesException.class, () -> verificador.verificar(env));

        assertEquals(java.util.List.of("Falta la variable de entorno SPRING_DATASOURCE_PASSWORD"), ex.getProblemas());
    }

    @Test
    void valorVacio_cuentaComoFaltante() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "   ")
                .withProperty("jwt.secret", SECRETO_VALIDO);

        assertThrows(VariablesEntornoFaltantesException.class, () -> verificador.verificar(env));
    }

    @Test
    void jwtSecretCorta_falla() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "clave")
                .withProperty("jwt.secret", "corta");

        VariablesEntornoFaltantesException ex =
                assertThrows(VariablesEntornoFaltantesException.class, () -> verificador.verificar(env));

        assertTrue(ex.getProblemas().get(0).startsWith("JWT_SECRET es demasiado corta"));
    }

    @Test
    void configuracionCompleta_noFalla() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "clave")
                .withProperty("jwt.secret", SECRETO_VALIDO);

        assertDoesNotThrow(() -> verificador.verificar(env));
    }

    @Test
    void analizador_muestraMensajeClaro() {
        VariablesEntornoFaltantesException ex = new VariablesEntornoFaltantesException(
                java.util.List.of("Falta la variable de entorno JWT_SECRET"));

        FailureAnalysis analisis = new VariablesEntornoFailureAnalyzer().analyze(ex);

        assertNotNull(analisis);
        assertTrue(analisis.getDescription().contains("Falta la variable de entorno JWT_SECRET"));
        assertTrue(analisis.getAction().contains("Edit Configurations"));
    }
}
