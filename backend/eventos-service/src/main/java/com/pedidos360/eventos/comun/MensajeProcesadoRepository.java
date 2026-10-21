package com.pedidos360.eventos.comun;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeProcesadoRepository extends JpaRepository<MensajeProcesado, Long> {
    boolean existsByConsumidorAndEventoId(String consumidor, String eventoId);
}
