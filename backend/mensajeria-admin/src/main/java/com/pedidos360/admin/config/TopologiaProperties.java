package com.pedidos360.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Valores de la topología leídos de application.yml (prefijo mensajeria.topologia). */
@ConfigurationProperties(prefix = "mensajeria.topologia")
public record TopologiaProperties(
        boolean crearAlIniciar,
        String topico,
        int maxReceiveCount,
        List<Cola> colas) {

    public record Cola(
            String nombre,
            String dlq,
            String consumidorId,
            int visibilityTimeoutSegundos,
            int retencionSegundos,
            int dlqRetencionSegundos,
            String filtro) {}
}
