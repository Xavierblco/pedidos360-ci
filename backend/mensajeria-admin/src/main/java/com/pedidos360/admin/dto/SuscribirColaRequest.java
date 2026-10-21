package com.pedidos360.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Suscribe una cola SQS a un tópico.
 * @param filtro filter policy JSON sobre atributos del mensaje, ej. {"tipoEntrega":["ENVIO"]}; vacío = fanout.
 * @param rawMessageDelivery true (recomendado): la cola recibe el JSON del evento tal cual, sin el sobre de SNS.
 */
public record SuscribirColaRequest(@NotBlank String cola, String filtro, Boolean rawMessageDelivery) {}
