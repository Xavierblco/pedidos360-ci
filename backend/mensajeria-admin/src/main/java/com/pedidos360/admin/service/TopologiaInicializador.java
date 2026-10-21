package com.pedidos360.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Al arrancar mensajeria-admin, crea/actualiza la topología en AWS (mensajeria.topologia.crear-al-iniciar). */
@Component
public class TopologiaInicializador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TopologiaInicializador.class);

    private final TopologiaService topologia;
    private final boolean crearAlIniciar;

    public TopologiaInicializador(TopologiaService topologia,
                                  @Value("${mensajeria.topologia.crear-al-iniciar:true}") boolean crearAlIniciar) {
        this.topologia = topologia;
        this.crearAlIniciar = crearAlIniciar;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!crearAlIniciar) {
            log.info("[topologia] crear-al-iniciar=false: no se modifica AWS al iniciar");
            return;
        }
        try {
            topologia.aplicar();
            log.info("[topologia] Topología aplicada correctamente");
        } catch (RuntimeException e) {
            // No se cae la app: se puede reintentar con POST /api/admin/topologia/aplicar
            log.error("[topologia] No se pudo aplicar la topología: {}", e.getMessage());
        }
    }
}
