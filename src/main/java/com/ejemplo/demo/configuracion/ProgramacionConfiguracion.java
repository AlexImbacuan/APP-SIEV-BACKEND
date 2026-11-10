package com.ejemplo.demo.configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas (@Scheduled), como la sincronización con Siigo. */
@Configuration
@EnableScheduling
public class ProgramacionConfiguracion {
}
