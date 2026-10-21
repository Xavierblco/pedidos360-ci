package com.pedidos360.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.admin.dto.Suscripcion;
import com.pedidos360.admin.dto.Topico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Operaciones sobre tópicos y suscripciones SNS. */
@Service
public class TopicoService {

    private static final Logger log = LoggerFactory.getLogger(TopicoService.class);

    private final SnsClient sns;
    private final ColaService colas;
    private final ObjectMapper mapper;

    public TopicoService(SnsClient sns, ColaService colas, ObjectMapper mapper) {
        this.sns = sns;
        this.colas = colas;
        this.mapper = mapper;
    }

    public List<Topico> listar() {
        return sns.listTopicsPaginator(ListTopicsRequest.builder().build()).topics().stream()
                .map(t -> new Topico(Aws.nombreDesdeArn(t.topicArn()), t.topicArn()))
                .sorted((a, b) -> a.nombre().compareTo(b.nombre()))
                .toList();
    }

    public Optional<String> arnSiExiste(String nombre) {
        return sns.listTopicsPaginator(ListTopicsRequest.builder().build()).topics().stream()
                .map(t -> t.topicArn())
                .filter(arn -> arn.endsWith(":" + nombre))
                .findFirst();
    }

    public String arn(String nombre) {
        return arnSiExiste(nombre).orElseThrow(() -> NotFoundException.builder()
                .message("No existe el tópico " + nombre).build());
    }

    /** createTopic es idempotente: si ya existe, devuelve el mismo ARN. */
    public Topico crear(String nombre) {
        String arn = sns.createTopic(r -> r.name(nombre)).topicArn();
        log.info("Tópico {} listo ({})", nombre, arn);
        return new Topico(nombre, arn);
    }

    public void eliminar(String nombre) {
        String arn = arn(nombre);
        sns.deleteTopic(r -> r.topicArn(arn));
        log.info("Tópico {} eliminado", nombre);
    }

    public List<Suscripcion> suscripciones(String nombreTopico) {
        String topicArn = arn(nombreTopico);
        return sns.listSubscriptionsByTopicPaginator(r -> r.topicArn(topicArn)).subscriptions().stream()
                .map(s -> {
                    boolean pendiente = "PendingConfirmation".equals(s.subscriptionArn());
                    Map<String, String> a = pendiente ? Map.of()
                            : sns.getSubscriptionAttributes(r -> r.subscriptionArn(s.subscriptionArn())).attributes();
                    String cola = "sqs".equals(s.protocol()) ? Aws.nombreDesdeArn(s.endpoint()) : null;
                    return new Suscripcion(s.subscriptionArn(), s.protocol(), s.endpoint(), cola,
                            "true".equalsIgnoreCase(a.get("RawMessageDelivery")),
                            a.get("FilterPolicy"), pendiente);
                })
                .toList();
    }

    /**
     * Suscribe una cola al tópico (idempotente):
     * 1) Policy de la cola que permite a ESTE tópico enviarle mensajes (sin esto SNS no puede escribir en SQS).
     * 2) Suscripción con RawMessageDelivery y, si corresponde, FilterPolicy sobre los atributos del mensaje.
     */
    public Suscripcion suscribirCola(String nombreTopico, String nombreCola, String filtro, boolean raw) {
        String topicArn = arn(nombreTopico);
        String colaUrl = colas.url(nombreCola);
        String colaArn = colas.arn(colaUrl);
        String filtroNormalizado = (filtro == null || filtro.isBlank()) ? null : validarFiltro(filtro);

        colas.establecerPolicy(colaUrl, policyColaParaTopico(colaArn, topicArn));

        Optional<String> existente = sns.listSubscriptionsByTopicPaginator(r -> r.topicArn(topicArn)).subscriptions()
                .stream().filter(s -> colaArn.equals(s.endpoint())).map(s -> s.subscriptionArn()).findFirst();

        String subArn;
        if (existente.isPresent() && !"PendingConfirmation".equals(existente.get())) {
            subArn = existente.get();
            sns.setSubscriptionAttributes(r -> r.subscriptionArn(subArn)
                    .attributeName("RawMessageDelivery").attributeValue(String.valueOf(raw)));
            if (filtroNormalizado != null) {
                // El scope por defecto de FilterPolicy es MessageAttributes (filtra por atributos del mensaje)
                sns.setSubscriptionAttributes(r -> r.subscriptionArn(subArn)
                        .attributeName("FilterPolicy").attributeValue(filtroNormalizado));
            } else if (sns.getSubscriptionAttributes(r -> r.subscriptionArn(subArn)).attributes().containsKey("FilterPolicy")) {
                // "{}" = quitar el filtro: la suscripción vuelve a ser fanout
                sns.setSubscriptionAttributes(r -> r.subscriptionArn(subArn)
                        .attributeName("FilterPolicy").attributeValue("{}"));
            }
            log.info("Suscripción {} → {} actualizada (raw={}, filtro={})", nombreTopico, nombreCola, raw, filtroNormalizado);
        } else {
            Map<String, String> atributos = new HashMap<>();
            atributos.put("RawMessageDelivery", String.valueOf(raw));
            if (filtroNormalizado != null) {
                atributos.put("FilterPolicy", filtroNormalizado);
            }
            subArn = sns.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(colaArn)
                    .attributes(atributos).returnSubscriptionArn(true)).subscriptionArn();
            log.info("Suscripción {} → {} creada (raw={}, filtro={})", nombreTopico, nombreCola, raw, filtroNormalizado);
        }
        return new Suscripcion(subArn, "sqs", colaArn, nombreCola, raw, filtroNormalizado, false);
    }

    public void desuscribir(String subscriptionArn) {
        sns.unsubscribe(r -> r.subscriptionArn(subscriptionArn));
        log.info("Suscripción {} eliminada", subscriptionArn);
    }

    public String publicar(String nombreTopico, String cuerpo, Map<String, String> atributos) {
        String topicArn = arn(nombreTopico);
        Map<String, MessageAttributeValue> attrs = new HashMap<>();
        if (atributos != null) {
            atributos.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    attrs.put(k, MessageAttributeValue.builder().dataType("String").stringValue(v).build());
                }
            });
        }
        String id = sns.publish(r -> r.topicArn(topicArn).message(cuerpo).messageAttributes(attrs)).messageId();
        log.info("Mensaje de prueba publicado en {} (messageId={}, atributos={})", nombreTopico, id, atributos);
        return id;
    }

    private String validarFiltro(String filtro) {
        try {
            return mapper.writeValueAsString(mapper.readTree(filtro));
        } catch (Exception e) {
            throw new IllegalArgumentException("El filtro no es un JSON válido: " + filtro);
        }
    }

    private static String policyColaParaTopico(String colaArn, String topicArn) {
        return """
                {"Version":"2012-10-17","Statement":[{"Sid":"PermitirTopicoSns","Effect":"Allow",\
                "Principal":{"Service":"sns.amazonaws.com"},"Action":"sqs:SendMessage","Resource":"%s",\
                "Condition":{"ArnEquals":{"aws:SourceArn":"%s"}}}]}""".formatted(colaArn, topicArn);
    }
}
