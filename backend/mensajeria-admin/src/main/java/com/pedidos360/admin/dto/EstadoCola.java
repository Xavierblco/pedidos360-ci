package com.pedidos360.admin.dto;

/** Estado y configuración de una cola SQS. */
public record EstadoCola(
        String nombre,
        String url,
        String arn,
        long disponibles,
        long enProceso,
        long retrasados,
        Integer visibilityTimeoutSegundos,
        Integer retencionSegundos,
        String dlq,
        Integer maxReceiveCount) {}
