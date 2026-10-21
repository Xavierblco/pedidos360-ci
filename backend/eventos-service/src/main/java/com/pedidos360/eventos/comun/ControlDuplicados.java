package com.pedidos360.eventos.comun;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Idempotencia: SQS entrega "al menos una vez", así que el mismo evento puede llegar dos veces.
 * La marca se guarda DESPUÉS de procesar con éxito; si se guardara antes, un reintento por error
 * transitorio se confundiría con un duplicado y el mensaje se perdería.
 */
@Component
public class ControlDuplicados {

    private final MensajeProcesadoRepository repository;

    public ControlDuplicados(MensajeProcesadoRepository repository) {
        this.repository = repository;
    }

    public boolean yaProcesado(String consumidor, String eventoId) {
        return repository.existsByConsumidorAndEventoId(consumidor, eventoId);
    }

    /** Devuelve false si otra instancia lo registró al mismo tiempo (restricción única en BD). */
    public boolean registrar(String consumidor, String eventoId) {
        try {
            repository.save(new MensajeProcesado(consumidor, eventoId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
