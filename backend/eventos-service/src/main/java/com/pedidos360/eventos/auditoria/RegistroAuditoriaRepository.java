package com.pedidos360.eventos.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findByPedidoIdOrderByRegistradoEnAsc(Long pedidoId);
}
