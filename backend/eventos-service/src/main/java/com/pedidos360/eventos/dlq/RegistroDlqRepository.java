package com.pedidos360.eventos.dlq;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroDlqRepository extends JpaRepository<RegistroDlq, Long> {
    boolean existsByMessageId(String messageId);
    List<RegistroDlq> findTop50ByOrderByDetectadoEnDesc();
}
