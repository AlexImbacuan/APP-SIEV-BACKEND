package com.ejemplo.demo.configuracion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprueba las variables de entorno obligatorias ANTES de crear cualquier bean
 * (y por tanto antes de conectarse a la base de datos).
 *
 * Sin esta comprobación, si falta SPRING_DATASOURCE_PASSWORD Spring envía el texto
 * literal "${SPRING_DATASOURCE_PASSWORD}" como contraseña y el error que aparece es
 * un confuso "password authentication failed" de Postgres.
 *
 * Se registra en META-INF/spring.factories.
 */
public class VerificadorVariablesEntorno implements EnvironmentPostProcessor, Ordered {

    /** HS256 necesita una clave de al menos 256 bits (32 bytes). */
    static final int LARGO_MINIMO_JWT_SECRET = 32;

    /** Propiedad de Spring -> variable de entorno que la alimenta. */
    static final Map<String, String> OBLIGATORIAS = new LinkedHashMap<>();

    static {
        OBLIGATORIAS.put("spring.datasource.password", "SPRING_DATASOURCE_PASSWORD");
        OBLIGATORIAS.put("jwt.secret", "JWT_SECRET");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        verificar(environment);
    }

    void verificar(ConfigurableEnvironment environment) {

        List<String> problemas = new ArrayList<>();

        OBLIGATORIAS.forEach((propiedad, variable) -> {
            if (valorDe(environment, propiedad) == null) {
                problemas.add("Falta la variable de entorno " + variable);
            }
        });

        String secreto = valorDe(environment, "jwt.secret");
        if (secreto != null && secreto.getBytes(StandardCharsets.UTF_8).length < LARGO_MINIMO_JWT_SECRET) {
            problemas.add("JWT_SECRET es demasiado corta: debe tener al menos "
                    + LARGO_MINIMO_JWT_SECRET + " caracteres");
        }

        if (!problemas.isEmpty()) {
            throw new VariablesEntornoFaltantesException(problemas);
        }
    }

    /** Valor resuelto de la propiedad, o null si falta, está vacía o quedó como "${...}" sin resolver. */
    private static String valorDe(ConfigurableEnvironment environment, String propiedad) {
        try {
            String valor = environment.getProperty(propiedad);
            if (valor == null || valor.isBlank() || valor.contains("${")) {
                return null;
            }
            return valor;
        } catch (IllegalArgumentException placeholderSinResolver) {
            return null;
        }
    }

    /** Después de que Spring haya cargado application.properties y las variables de entorno. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
