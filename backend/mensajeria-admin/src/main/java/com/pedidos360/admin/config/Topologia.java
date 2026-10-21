package com.pedidos360.admin.config;

import java.util.List;

/**
 * Descripción inmutable y validada de la topología de mensajería de Pedidos360.
 * La construye {@link TopologiaMensajeria} y la aplica TopologiaService.
 */
public record Topologia(String topico, int maxReceiveCount, List<ColaDeclarada> colas) {

    /**
     * Una cola de trabajo con su DLQ.
     * @param filtroSuscripcion filter policy JSON sobre los atributos del mensaje; null = fanout (recibe todo).
     */
    public record ColaDeclarada(
            String nombre,
            String dlq,
            String consumidorId,
            int visibilityTimeoutSegundos,
            int retencionSegundos,
            int dlqRetencionSegundos,
            String filtroSuscripcion) {

        public boolean tieneFiltro() {
            return filtroSuscripcion != null;
        }
    }

    public ColaDeclarada cola(String nombre) {
        return colas.stream().filter(c -> c.nombre().equals(nombre)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La cola " + nombre + " no es parte de la topología"));
    }
}
