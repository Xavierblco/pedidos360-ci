package com.pedidos360.pedidos.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Publica los eventos de pedido en el tópico SNS. Los atributos del mensaje (tipoEvento, tipoEntrega,
 * estado) son los que usan las filter policies de las suscripciones para el enrutamiento selectivo.
 */
@Component
@ConditionalOnProperty(name = "mensajeria.habilitada", havingValue = "true", matchIfMissing = true)
public class SnsEventoPublisher implements EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsEventoPublisher.class);

    private final SnsClient sns;
    private final ObjectMapper mapper;
    private final String nombreTopico;
    private volatile String topicArn;

    public SnsEventoPublisher(SnsClient sns, ObjectMapper mapper,
                              @Value("${mensajeria.topico-eventos}") String nombreTopico) {
        this.sns = sns;
        this.mapper = mapper;
        this.nombreTopico = nombreTopico;
    }

    @Override
    public void publicar(EventoPedido evento) {
        try {
            String cuerpo = mapper.writeValueAsString(evento);
            Map<String, MessageAttributeValue> atributos = new HashMap<>();
            agregar(atributos, "eventoId", evento.eventoId());
            agregar(atributos, "tipoEvento", evento.tipoEvento());
            agregar(atributos, "tipoEntrega", evento.tipoEntrega());
            agregar(atributos, "estado", evento.estado());

            String messageId = sns.publish(r -> r.topicArn(topicArn()).message(cuerpo).messageAttributes(atributos))
                    .messageId();
            log.info("Evento {} publicado: pedido={} tipoEntrega={} eventoId={} snsMessageId={}",
                    evento.tipoEvento(), evento.pedidoId(), evento.tipoEntrega(), evento.eventoId(), messageId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.eventoId(), e);
        } catch (RuntimeException e) {
            // El pedido ya quedó guardado: no se revierte por una falla de mensajería, pero queda registrado.
            log.error("No se pudo publicar el evento {} del pedido {}: {}", evento.tipoEvento(), evento.pedidoId(), e.getMessage());
        }
    }

    /** createTopic es idempotente: si el tópico existe, devuelve su ARN. Se resuelve una sola vez. */
    private String topicArn() {
        if (topicArn == null) {
            topicArn = sns.createTopic(r -> r.name(nombreTopico)).topicArn();
        }
        return topicArn;
    }

    private static void agregar(Map<String, MessageAttributeValue> atributos, String nombre, String valor) {
        if (valor != null && !valor.isBlank()) {
            atributos.put(nombre, MessageAttributeValue.builder().dataType("String").stringValue(valor).build());
        }
    }
}
