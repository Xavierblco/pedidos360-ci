package com.pedidos360.admin.service;

import com.pedidos360.admin.config.Topologia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica en AWS la topología declarada en {@link com.pedidos360.admin.config.TopologiaMensajeria}.
 * Es idempotente: crea lo que falta y actualiza lo que existe, en este orden:
 * tópico → DLQ → cola con Redrive Policy → policy de la cola → suscripción (raw + filtro).
 */
@Service
public class TopologiaService {

    private static final Logger log = LoggerFactory.getLogger(TopologiaService.class);

    private final Topologia topologia;
    private final ColaService colas;
    private final TopicoService topicos;

    public TopologiaService(Topologia topologia, ColaService colas, TopicoService topicos) {
        this.topologia = topologia;
        this.colas = colas;
        this.topicos = topicos;
    }

    public Topologia declarada() {
        return topologia;
    }

    public List<String> aplicar() {
        List<String> pasos = new ArrayList<>();
        topicos.crear(topologia.topico());
        pasos.add("Tópico " + topologia.topico());

        for (Topologia.ColaDeclarada c : topologia.colas()) {
            // 1. DLQ primero: la Redrive Policy de la cola necesita su ARN
            String dlqUrl = colas.crearOActualizar(c.dlq(), Map.of(
                    QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(c.dlqRetencionSegundos())));
            pasos.add("DLQ " + c.dlq() + " (retención " + c.dlqRetencionSegundos() + " s)");

            // 2. Cola de trabajo con visibility timeout, retención y Redrive Policy
            Map<QueueAttributeName, String> atributos = new EnumMap<>(QueueAttributeName.class);
            atributos.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(c.visibilityTimeoutSegundos()));
            atributos.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(c.retencionSegundos()));
            atributos.put(QueueAttributeName.REDRIVE_POLICY, colas.redrivePolicy(dlqUrl, topologia.maxReceiveCount()));
            colas.crearOActualizar(c.nombre(), atributos);
            pasos.add("Cola " + c.nombre() + " (visibility " + c.visibilityTimeoutSegundos() + " s, retención "
                    + c.retencionSegundos() + " s, maxReceiveCount " + topologia.maxReceiveCount() + " → " + c.dlq() + ")");

            // 3. Suscripción al tópico con raw message delivery y filtro opcional
            topicos.suscribirCola(topologia.topico(), c.nombre(), c.filtroSuscripcion(), true);
            pasos.add("Suscripción " + topologia.topico() + " → " + c.nombre()
                    + (c.tieneFiltro() ? " con filtro " + c.filtroSuscripcion() : " (fanout, sin filtro)"));
        }
        pasos.forEach(p -> log.info("[topologia] {}", p));
        return pasos;
    }
}
