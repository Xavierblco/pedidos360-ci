package com.pedidos360.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.admin.dto.CrearColaRequest;
import com.pedidos360.admin.dto.EstadoCola;
import com.pedidos360.admin.dto.ResultadoReproceso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.util.*;

/** Operaciones sobre colas SQS. Es la única clase del admin que habla con el SDK de SQS. */
@Service
public class ColaService {

    private static final Logger log = LoggerFactory.getLogger(ColaService.class);
    /** Atributos que agrega eventos-service al mandar a la DLQ; se quitan al reprocesar. */
    private static final Set<String> ATRIBUTOS_DE_FALLA = Set.of("causa", "consumidor", "intento", "colaOrigen");
    private static final int MAX_REPROCESO = 500;

    private final SqsAsyncClient sqs;
    private final ObjectMapper mapper;

    public ColaService(SqsAsyncClient sqs, ObjectMapper mapper) {
        this.sqs = sqs;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ consulta

    public String url(String nombre) {
        return Aws.esperar(sqs.getQueueUrl(r -> r.queueName(nombre))).queueUrl();
    }

    public Optional<String> urlSiExiste(String nombre) {
        try {
            return Optional.of(url(nombre));
        } catch (QueueDoesNotExistException e) {
            return Optional.empty();
        }
    }

    public String arn(String url) {
        return Aws.esperar(sqs.getQueueAttributes(r -> r.queueUrl(url).attributeNames(QueueAttributeName.QUEUE_ARN)))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    public List<EstadoCola> listar(String prefijo) {
        List<String> urls = Aws.esperar(sqs.listQueues(r -> {
            if (prefijo != null && !prefijo.isBlank()) r.queueNamePrefix(prefijo);
        })).queueUrls();
        List<EstadoCola> estados = new ArrayList<>();
        for (String u : urls) {
            try {
                estados.add(estadoPorUrl(u));
            } catch (QueueDoesNotExistException e) {
                // recién eliminada: se omite
            }
        }
        estados.sort(Comparator.comparing(EstadoCola::nombre));
        return estados;
    }

    public EstadoCola estado(String nombre) {
        return estadoPorUrl(url(nombre));
    }

    private EstadoCola estadoPorUrl(String url) {
        Map<QueueAttributeName, String> a = Aws.esperar(sqs.getQueueAttributes(r -> r.queueUrl(url)
                .attributeNames(QueueAttributeName.ALL))).attributes();
        String dlq = null;
        Integer maxReceive = null;
        String redrive = a.get(QueueAttributeName.REDRIVE_POLICY);
        if (redrive != null && !redrive.isBlank()) {
            JsonNode json = leerJson(redrive);
            dlq = Aws.nombreDesdeArn(json.path("deadLetterTargetArn").asText(null));
            maxReceive = json.path("maxReceiveCount").asInt();
        }
        String arn = a.get(QueueAttributeName.QUEUE_ARN);
        return new EstadoCola(Aws.nombreDesdeArn(arn), url, arn,
                largo(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)),
                largo(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)),
                largo(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED)),
                entero(a.get(QueueAttributeName.VISIBILITY_TIMEOUT)),
                entero(a.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)),
                dlq, maxReceive);
    }

    // ------------------------------------------------------------------ creación / eliminación

    /**
     * Crea la cola (o actualiza sus atributos si ya existe). Idempotente: se puede ejecutar muchas veces.
     */
    public String crearOActualizar(String nombre, Map<QueueAttributeName, String> atributos) {
        Optional<String> existente = urlSiExiste(nombre);
        if (existente.isPresent()) {
            if (!atributos.isEmpty()) {
                Aws.esperar(sqs.setQueueAttributes(r -> r.queueUrl(existente.get()).attributes(atributos)));
            }
            log.info("Cola {} ya existía: atributos actualizados", nombre);
            return existente.get();
        }
        String url = Aws.esperar(sqs.createQueue(r -> r.queueName(nombre).attributes(atributos))).queueUrl();
        log.info("Cola {} creada", nombre);
        return url;
    }

    /** Redrive Policy: después de maxReceiveCount recepciones sin ACK, SQS mueve el mensaje a la DLQ. */
    public String redrivePolicy(String dlqUrl, int maxReceiveCount) {
        return "{\"deadLetterTargetArn\":\"" + arn(dlqUrl) + "\",\"maxReceiveCount\":\"" + maxReceiveCount + "\"}";
    }

    public EstadoCola crear(CrearColaRequest req) {
        Map<QueueAttributeName, String> atributos = new EnumMap<>(QueueAttributeName.class);
        if (req.retencionSegundos() != null) {
            atributos.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, req.retencionSegundos().toString());
        }
        if (req.visibilityTimeoutSegundos() != null) {
            atributos.put(QueueAttributeName.VISIBILITY_TIMEOUT, req.visibilityTimeoutSegundos().toString());
        }
        if (req.dlq() != null && !req.dlq().isBlank()) {
            if (req.dlq().equals(req.nombre())) {
                throw new IllegalArgumentException("La DLQ no puede ser la misma cola");
            }
            String dlqUrl = crearOActualizar(req.dlq(), Map.of(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"));
            int max = req.maxReceiveCount() != null ? req.maxReceiveCount() : 3;
            atributos.put(QueueAttributeName.REDRIVE_POLICY, redrivePolicy(dlqUrl, max));
        }
        return estadoPorUrl(crearOActualizar(req.nombre(), atributos));
    }

    public void eliminar(String nombre) {
        String u = url(nombre);
        Aws.esperar(sqs.deleteQueue(r -> r.queueUrl(u)));
        log.info("Cola {} eliminada", nombre);
    }

    /** Borra TODOS los mensajes de la cola. SQS permite una purga cada 60 segundos por cola. */
    public void purgar(String nombre) {
        String u = url(nombre);
        Aws.esperar(sqs.purgeQueue(r -> r.queueUrl(u)));
        log.warn("Cola {} purgada", nombre);
    }

    public void establecerPolicy(String url, String policyJson) {
        Aws.esperar(sqs.setQueueAttributes(r -> r.queueUrl(url).attributes(Map.of(QueueAttributeName.POLICY, policyJson))));
    }

    // ------------------------------------------------------------------ DLQ

    /** Nombre de la DLQ de una cola, leído de su Redrive Policy. */
    public String dlqDe(String nombreCola) {
        EstadoCola estado = estado(nombreCola);
        if (estado.dlq() == null) {
            throw new IllegalArgumentException("La cola " + nombreCola + " no tiene DLQ (Redrive Policy) configurada");
        }
        return estado.dlq();
    }

    /**
     * Mueve los mensajes de la DLQ de vuelta a su cola, conservando los atributos originales
     * (tipoEvento, tipoEntrega, eventoId...) y quitando los de diagnóstico (causa, intento...).
     * El receipt handle pertenece a la DLQ, así que el borrado se hace en la DLQ.
     */
    public ResultadoReproceso reprocesarDlq(String nombreCola) {
        String dlq = dlqDe(nombreCola);
        String dlqUrl = url(dlq);
        String destinoUrl = url(nombreCola);
        int movidos = 0;
        while (movidos < MAX_REPROCESO) {
            List<Message> mensajes = Aws.esperar(sqs.receiveMessage(r -> r.queueUrl(dlqUrl)
                    .maxNumberOfMessages(10).waitTimeSeconds(1).visibilityTimeout(30)
                    .messageAttributeNames("All"))).messages();
            if (mensajes.isEmpty()) {
                break;
            }
            for (Message m : mensajes) {
                Map<String, MessageAttributeValue> atributos = new HashMap<>(m.messageAttributes());
                atributos.keySet().removeAll(ATRIBUTOS_DE_FALLA);
                atributos.put("reprocesado", MessageAttributeValue.builder().dataType("String").stringValue("true").build());
                Aws.esperar(sqs.sendMessage(r -> r.queueUrl(destinoUrl).messageBody(m.body()).messageAttributes(atributos)));
                Aws.esperar(sqs.deleteMessage(r -> r.queueUrl(dlqUrl).receiptHandle(m.receiptHandle())));
                movidos++;
            }
        }
        log.info("Reprocesados {} mensajes de {} a {}", movidos, dlq, nombreCola);
        return new ResultadoReproceso(dlq, nombreCola, movidos);
    }

    public String purgarDlq(String nombreCola) {
        String dlq = dlqDe(nombreCola);
        purgar(dlq);
        return dlq;
    }

    // ------------------------------------------------------------------ util

    private JsonNode leerJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static long largo(String v) {
        return v == null ? 0 : Long.parseLong(v);
    }

    private static Integer entero(String v) {
        return v == null ? null : Integer.valueOf(v);
    }
}
