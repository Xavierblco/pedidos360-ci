package com.pedidos360.eventos.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.eventos.config.MensajeriaProperties;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Flujo común de todos los consumidores (ACK manual explícito):
 * <ol>
 *   <li>Convierte el JSON en {@link EventoPedido} y lo valida.</li>
 *   <li>Si el evento ya se procesó (idempotencia en BD) → ACK y se ignora.</li>
 *   <li>Llama a la lógica de dominio. Éxito → registra el evento y ACK.</li>
 *   <li>{@link ErrorDefinitivo} → envía a la DLQ con atributos causa/intento/consumidor y ACK.</li>
 *   <li>{@link ErrorTransitorio} (o error inesperado) → NO hay ACK: el mensaje vuelve a estar visible al
 *       vencer el visibility timeout y se reintenta. En el último intento (maxReceiveCount) se envía a la DLQ
 *       con la causa; si eso fallara, la Redrive Policy de SQS lo mueve igual.</li>
 * </ol>
 * La lógica de negocio de cada dominio no conoce SQS: solo recibe un EventoPedido.
 */
@Component
public class ProcesadorMensajes {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorMensajes.class);

    private final ObjectMapper mapper;
    private final ControlDuplicados duplicados;
    private final ColasSqs colas;
    private final MensajeriaProperties props;

    public ProcesadorMensajes(ObjectMapper mapper, ControlDuplicados duplicados, ColasSqs colas,
                              MensajeriaProperties props) {
        this.mapper = mapper;
        this.duplicados = duplicados;
        this.colas = colas;
        this.props = props;
    }

    public void procesar(String consumidor, String cuerpo, Message original, Acknowledgement ack,
                         Consumer<EventoPedido> logicaDeDominio) {
        int intento = intento(original);
        int max = props.maxReceiveCount();
        String messageId = original.messageId();
        EventoPedido evento = null;
        try {
            evento = leer(cuerpo);
            log.info("[{}] Recibido {} pedido={} eventoId={} messageId={} intento={}/{}",
                    consumidor, evento.tipoEvento(), evento.pedidoId(), evento.eventoId(), messageId, intento, max);

            if (duplicados.yaProcesado(consumidor, evento.eventoId())) {
                log.warn("[{}] Evento {} duplicado, ya fue procesado: se confirma y se ignora", consumidor, evento.eventoId());
                ack.acknowledge();
                return;
            }

            logicaDeDominio.accept(evento);

            if (!duplicados.registrar(consumidor, evento.eventoId())) {
                log.warn("[{}] Evento {} registrado en paralelo por otra instancia", consumidor, evento.eventoId());
            }
            ack.acknowledge();
            log.info("[{}] OK pedido={} eventoId={} (ACK enviado)", consumidor, evento.pedidoId(), evento.eventoId());

        } catch (ErrorDefinitivo e) {
            log.error("[{}] ERROR DEFINITIVO messageId={} intento={}/{} causa=\"{}\" -> se envía directo a la DLQ",
                    consumidor, messageId, intento, max, e.getMessage());
            if (enviarADlq(consumidor, original, e.getMessage(), intento)) {
                ack.acknowledge();
            }
        } catch (RuntimeException e) {
            String tipo = e instanceof ErrorTransitorio ? "ERROR TRANSITORIO" : "ERROR INESPERADO";
            if (intento >= max) {
                // Último intento: lo mandamos nosotros a la DLQ para dejar registrada la causa.
                // Si esto fallara, la Redrive Policy de SQS lo mueve igual (red de seguridad).
                log.error("[{}] {} messageId={} intento={}/{} causa=\"{}\" -> se agotaron los intentos, se envía a la DLQ",
                        consumidor, tipo, messageId, intento, max, e.getMessage());
                if (enviarADlq(consumidor, original, "Agotó " + max + " intentos: " + e.getMessage(), intento)) {
                    ack.acknowledge();
                }
            } else {
                log.warn("[{}] {} messageId={} intento={}/{} causa=\"{}\" -> sin ACK; se reintentará al vencer el visibility timeout",
                        consumidor, tipo, messageId, intento, max, e.getMessage());
            }
        }
    }

    private EventoPedido leer(String cuerpo) {
        EventoPedido evento;
        try {
            evento = mapper.readValue(cuerpo, EventoPedido.class);
        } catch (Exception e) {
            throw new ErrorDefinitivo("Mensaje con formato inválido (no es un EventoPedido JSON)");
        }
        if (evento.eventoId() == null || evento.eventoId().isBlank()) {
            throw new ErrorDefinitivo("Evento sin eventoId");
        }
        if (evento.pedidoId() == null) {
            throw new ErrorDefinitivo("Evento sin pedidoId");
        }
        return evento;
    }

    /** Copia el mensaje a la DLQ conservando sus atributos y agregando la causa. */
    private boolean enviarADlq(String consumidor, Message original, String causa, int intento) {
        MensajeriaProperties.Consumidor config = props.consumidor(consumidor);
        Map<String, MessageAttributeValue> atributos = new HashMap<>(original.messageAttributes());
        atributos.put("causa", texto(causa));
        atributos.put("consumidor", texto(consumidor));
        atributos.put("intento", texto(String.valueOf(intento)));
        atributos.put("colaOrigen", texto(config.cola()));
        try {
            colas.cliente().sendMessage(r -> r.queueUrl(colas.url(config.dlq()))
                    .messageBody(original.body())
                    .messageAttributes(atributos)).join();
            return true;
        } catch (RuntimeException e) {
            // Si no se pudo escribir en la DLQ, no se confirma: SQS lo reintentará y, al final, lo moverá él mismo.
            log.error("[{}] No se pudo enviar a la DLQ {}: {}", consumidor, config.dlq(), e.getMessage());
            return false;
        }
    }

    private static int intento(Message m) {
        try {
            return Integer.parseInt(m.attributesAsStrings().getOrDefault("ApproximateReceiveCount", "1"));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static MessageAttributeValue texto(String valor) {
        String v = (valor == null || valor.isBlank()) ? "-" : valor;
        return MessageAttributeValue.builder().dataType("String").stringValue(v).build();
    }
}
