package com.pedidos360.eventos.despacho;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrdenDespachoRepository extends JpaRepository<OrdenDespacho, Long> {
    Optional<OrdenDespacho> findByPedidoId(Long pedidoId);
}
