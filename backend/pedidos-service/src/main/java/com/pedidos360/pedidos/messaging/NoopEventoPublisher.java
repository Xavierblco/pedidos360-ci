package com.pedidos360.pedidos.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Se usa cuando mensajeria.habilitada=false (tests o ejecución sin AWS). Solo registra el evento. */
@Component
@ConditionalOnProperty(name = "mensajeria.habilitada", havingValue = "false")
public class NoopEventoPublisher implements EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopEventoPublisher.class);

    @Override
    public void publicar(EventoPedido evento) {
        log.info("[mensajeria deshabilitada] Evento {} del pedido {} no publicado", evento.tipoEvento(), evento.pedidoId());
    }
}
