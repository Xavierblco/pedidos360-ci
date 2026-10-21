package com.pedidos360.pedidos.messaging;

import com.pedidos360.pedidos.entity.Pedido;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contrato del evento que viaja por SNS → SQS (cuerpo JSON del mensaje).
 * eventoId es único por evento y lo usan los consumidores para detectar duplicados.
 */
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
        String fecha) {

    public static EventoPedido de(Pedido pedido, TipoEvento tipo) {
        return new EventoPedido(
                UUID.randomUUID().toString(),
                tipo.name(),
                pedido.getId(),
                pedido.getUsuarioEmail(),
                pedido.getEstado() != null ? pedido.getEstado().name() : null,
                pedido.getTipoEntrega() != null ? pedido.getTipoEntrega() : "RETIRO",
                pedido.getDireccion(),
                pedido.getTotal(),
                pedido.getCantidad(),
                LocalDateTime.now().toString());
    }
}
