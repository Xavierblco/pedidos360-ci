package com.pedidos360.admin.dto;

/** Suscripción de un tópico. filtro = FilterPolicy (null = fanout). */
public record Suscripcion(
        String arn,
        String protocolo,
        String endpoint,
        String cola,
        boolean rawMessageDelivery,
        String filtro,
        boolean pendienteConfirmacion) {}
