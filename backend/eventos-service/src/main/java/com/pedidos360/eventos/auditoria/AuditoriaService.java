package com.pedidos360.eventos.auditoria;

import com.pedidos360.eventos.comun.ErrorDefinitivo;
import com.pedidos360.eventos.comun.ErrorTransitorio;
import com.pedidos360.eventos.comun.EventoPedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Dominio: trazabilidad. Guarda cada evento en la base de datos cloud. */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final RegistroAuditoriaRepository repository;

    public AuditoriaService(RegistroAuditoriaRepository repository) {
        this.repository = repository;
    }

    public void registrar(EventoPedido evento) {
        if (evento.total() == null || evento.total() <= 0) {
            throw new ErrorDefinitivo("Pedido " + evento.pedidoId() + " con total inválido: " + evento.total());
        }
        try {
            repository.save(new RegistroAuditoria(evento.eventoId(), evento.tipoEvento(), evento.pedidoId(),
                    evento.usuarioEmail(), evento.estado(), evento.total()));
        } catch (DataIntegrityViolationException e) {
            log.warn("[auditoria] Evento {} ya estaba registrado", evento.eventoId());
            return;
        } catch (DataAccessResourceFailureException e) {
            // La BD no responde: es transitorio, se deja sin ACK para reintentar.
            throw new ErrorTransitorio("Base de datos no disponible: " + e.getMessage());
        }
        log.info("[auditoria] {} del pedido {} registrado", evento.tipoEvento(), evento.pedidoId());
    }

    public List<RegistroAuditoria> historial(Long pedidoId) {
        return repository.findByPedidoIdOrderByRegistradoEnAsc(pedidoId);
    }
}
