package com.pedidos360.pedidos.messaging;

/**
 * Puerto de salida para eventos de dominio. La lógica de negocio (PedidoService) solo conoce
 * esta interfaz; no sabe nada de SNS ni del SDK de AWS.
 */
public interface EventoPublisher {

    void publicar(EventoPedido evento);
}
