package com.pedidos360.eventos.comun;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mismo contrato JSON que publica pedidos-service (com.pedidos360.pedidos.messaging.EventoPedido). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoPedido(
        String eventoId,
        String tipoEvento,
        Long pedidoId,
        String usuarioEmail,
        String estado,
        String tipoEntrega,
        String direccion,
        Double total,
        Integer cantidadItems,
        String fecha) {}
