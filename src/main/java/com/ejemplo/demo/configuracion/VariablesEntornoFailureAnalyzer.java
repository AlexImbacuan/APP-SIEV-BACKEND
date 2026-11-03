package com.ejemplo.demo.configuracion;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Muestra el error de variables faltantes en el bloque
 * "APPLICATION FAILED TO START" de Spring Boot, sin stack trace.
 * Se registra en META-INF/spring.factories.
 */
public class VariablesEntornoFailureAnalyzer extends AbstractFailureAnalyzer<VariablesEntornoFaltantesException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, VariablesEntornoFaltantesException cause) {

        String descripcion = "La aplicación no puede arrancar porque su configuración está incompleta:\n\n  - "
                + String.join("\n  - ", cause.getProblemas());

        String accion = """
                Define las variables de entorno que faltan y vuelve a arrancar:
                  - IntelliJ: Run > Edit Configurations... > DemoApplication > Environment variables
                  - Render:   tu servicio > Environment
                Los nombres deben coincidir exactamente (mayúsculas incluidas) y los valores van sin comillas.""";

        return new FailureAnalysis(descripcion, accion, cause);
    }
}
