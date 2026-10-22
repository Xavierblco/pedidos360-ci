package com.pedidos360.eventos.dlq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.eventos.comun.ColasSqs;
import com.pedidos360.eventos.config.MensajeriaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.util.List;
import java.util.Map;

/**
 * Consumidor de las 3 DLQ para logs. Lee cada DLQ con VisibilityTimeout = 0 (solo "mira" los mensajes,
 * no los oculta ni los borra), así la DLQ conserva los mensajes para la alarma de CloudWatch y para el
 * reprocesamiento desde mensajeria-admin. Cada mensaje se registra una sola vez (por messageId) en el log
 * y en la tabla registros_dlq, con su id, intento y causa.
 */
@Component
@ConditionalOnProperty(name = "mensajeria.dlq-monitor.habilitado", havingValue = "true", matchIfMissing = true)
public class DlqMonitor {

    private static final Logger log = LoggerFactory.getLogger(DlqMonitor.class);

    private final ColasSqs colas;
    private final MensajeriaProperties props;
    private final RegistroDlqRepository repository;
    private final ObjectMapper mapper;

    public DlqMonitor(ColasSqs colas, MensajeriaProperties props, RegistroDlqRepository repository, ObjectMapper mapper) {
        this.colas = colas;
        this.props = props;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${mensajeria.dlq-monitor.intervalo-ms}", initialDelay = 5000)
    public void revisar() {
        for (MensajeriaProperties.Consumidor c : props.consumidores().values()) {
            try {
                revisar(c.dlq());
            } catch (RuntimeException e) {
                log.warn("[dlq-monitor] No se pudo leer {}: {}", c.dlq(), e.getMessage());
            }
        }
    }

    private void revisar(String dlq) {
        List<Message> mensajes = colas.cliente().receiveMessage(r -> r
                .queueUrl(colas.url(dlq))
                .maxNumberOfMessages(10)
                .visibilityTimeout(0)
                .waitTimeSeconds(0)
                .messageAttributeNames("All")
                .messageSystemAttributeNames(MessageSystemAttributeName.ALL)).join().messages();

        for (Message m : mensajes) {
            if (repository.existsByMessageId(m.messageId())) {
                continue;
            }
            Map<String, MessageAttributeValue> attrs = m.messageAttributes();
            String causa = valor(attrs, "causa");
            String intento = valor(attrs, "intento");
            if (causa == null) {
                // Llegó por la Redrive Policy de SQS (red de seguridad): el consumidor no alcanzó a enviarlo él mismo.
                causa = "Superó maxReceiveCount=" + props.maxReceiveCount() + " (movido por la Redrive Policy de SQS)";
                intento = String.valueOf(props.maxReceiveCount());
            }
            String eventoId = valor(attrs, "eventoId");
            Long pedidoId = null;
            try {
                JsonNode json = mapper.readTree(m.body());
                if (eventoId == null && json.hasNonNull("eventoId")) eventoId = json.get("eventoId").asText();
                if (json.hasNonNull("pedidoId")) pedidoId = json.get("pedidoId").asLong();
            } catch (Exception ignorado) {
                // cuerpo no JSON: se registra igual
            }
            try {
                repository.save(new RegistroDlq(m.messageId(), dlq, eventoId, pedidoId, intento, causa, m.body()));
                log.error("[DLQ] cola={} messageId={} eventoId={} pedido={} intento={} causa=\"{}\"",
                        dlq, m.messageId(), eventoId, pedidoId, intento, causa);
            } catch (DataIntegrityViolationException e) {
                // otra instancia lo registró primero
            }
        }
    }

    private static String valor(Map<String, MessageAttributeValue> attrs, String nombre) {
        MessageAttributeValue v = attrs.get(nombre);
        return v != null ? v.stringValue() : null;
    }
}
