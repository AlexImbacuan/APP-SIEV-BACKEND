package com.ejemplo.demo.configuracion;

import java.util.List;

/** Lanzada al arrancar cuando faltan variables de entorno obligatorias. */
public class VariablesEntornoFaltantesException extends IllegalStateException {

    private final List<String> problemas;

    public VariablesEntornoFaltantesException(List<String> problemas) {
        super("Configuración incompleta: " + String.join("; ", problemas));
        this.problemas = List.copyOf(problemas);
    }

    public List<String> getProblemas() {
        return problemas;
    }
}
