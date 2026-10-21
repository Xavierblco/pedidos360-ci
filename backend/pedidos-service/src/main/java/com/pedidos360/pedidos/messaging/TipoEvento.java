package com.pedidos360.pedidos.messaging;

/** Tipos de evento de dominio que publica pedidos-service. Viajan como atributo "tipoEvento" del mensaje. */
public enum TipoEvento {
    PEDIDO_CREADO,
    PEDIDO_ESTADO_ACTUALIZADO
}
