package com.pedidos360.eventos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Única fuente de nombres de colas, DLQ e IDs de listener (application.yml, prefijo "mensajeria").
 * Los @SqsListener leen los mismos valores con placeholders ${mensajeria.consumidores.*}.
 */
@ConfigurationProperties(prefix = "mensajeria")
public record MensajeriaProperties(
        int maxReceiveCount,
        Map<String, Consumidor> consumidores,
        Simulacion simulacion,
        DlqMonitor dlqMonitor) {

    public record Consumidor(String id, String cola, String dlq) {}

    public record Simulacion(long demoraMs, boolean falloTransitorioNotificaciones) {}

    public record DlqMonitor(boolean habilitado, long intervaloMs) {}

    public Consumidor consumidor(String nombre) {
        Consumidor c = consumidores.get(nombre);
        if (c == null) {
            throw new IllegalArgumentException("Consumidor no configurado: " + nombre);
        }
        return c;
    }
}
