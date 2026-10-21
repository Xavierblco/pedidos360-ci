package com.pedidos360.eventos.comun;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resuelve y cachea la URL de una cola a partir de su nombre. */
@Component
public class ColasSqs {

    private final SqsAsyncClient sqs;
    private final Map<String, String> urls = new ConcurrentHashMap<>();

    public ColasSqs(SqsAsyncClient sqs) {
        this.sqs = sqs;
    }

    public String url(String nombreCola) {
        return urls.computeIfAbsent(nombreCola,
                n -> sqs.getQueueUrl(r -> r.queueName(n)).join().queueUrl());
    }

    public SqsAsyncClient cliente() {
        return sqs;
    }
}
