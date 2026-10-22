package com.pedidos360.eventos.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.eventos.config.MensajeriaProperties;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Prueba el flujo de ACK manual, idempotencia y envío a DLQ sin conectarse a AWS. */
class ProcesadorMensajesTest {

    private static final String JSON = """
            {"eventoId":"ev-1","tipoEvento":"PEDIDO_CREADO","pedidoId":10,"usuarioEmail":"a@b.cl",
             "estado":"PENDIENTE","tipoEntrega":"ENVIO","direccion":"Calle 123","total":9990.0}""";

    private ControlDuplicados duplicados;
    private SqsAsyncClient sqs;
    private Acknowledgement ack;
    private ProcesadorMensajes procesador;

    @BeforeEach
    void preparar() {
        duplicados = mock(ControlDuplicados.class);
        sqs = mock(SqsAsyncClient.class);
        ColasSqs colas = mock(ColasSqs.class);
        when(colas.cliente()).thenReturn(sqs);
        when(colas.url(anyString())).thenReturn("https://sqs/dlq");
        when(duplicados.registrar(anyString(), anyString())).thenReturn(true);
        ack = mock(Acknowledgement.class);
        MensajeriaProperties props = new MensajeriaProperties(3,
                Map.of("notificaciones", new MensajeriaProperties.Consumidor("consumidor-notificaciones",
                        "pedidos360-notificaciones-q", "pedidos360-notificaciones-dlq")),
                new MensajeriaProperties.Simulacion(0, false),
                new MensajeriaProperties.DlqMonitor(false, 15000));
        procesador = new ProcesadorMensajes(new ObjectMapper(), duplicados, colas, props);
    }

    private static Message mensaje(String cuerpo, int intento) {
        return Message.builder().messageId("m-1").body(cuerpo)
                .attributesWithStrings(Map.of("ApproximateReceiveCount", String.valueOf(intento)))
                .build();
    }

    @Test
    void exitoRegistraYConfirma() {
        AtomicInteger llamadas = new AtomicInteger();
        procesador.procesar("notificaciones", JSON, mensaje(JSON, 1), ack, e -> llamadas.incrementAndGet());
        assertEquals(1, llamadas.get());
        verify(duplicados).registrar("notificaciones", "ev-1");
        verify(ack).acknowledge();
    }

    @Test
    void duplicadoSeConfirmaSinProcesar() {
        when(duplicados.yaProcesado("notificaciones", "ev-1")).thenReturn(true);
        AtomicInteger llamadas = new AtomicInteger();
        procesador.procesar("notificaciones", JSON, mensaje(JSON, 2), ack, e -> llamadas.incrementAndGet());
        assertEquals(0, llamadas.get());
        verify(ack).acknowledge();
    }

    @Test
    void errorTransitorioNoConfirmaNiMarcaComoProcesado() {
        procesador.procesar("notificaciones", JSON, mensaje(JSON, 1), ack, e -> {
            throw new ErrorTransitorio("correo caído");
        });
        verify(ack, never()).acknowledge();
        verify(duplicados, never()).registrar(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorDefinitivoVaALaDlqYSeConfirma() {
        when(sqs.sendMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("x").build()));
        procesador.procesar("notificaciones", JSON, mensaje(JSON, 1), ack, e -> {
            throw new ErrorDefinitivo("datos inválidos");
        });
        verify(sqs).sendMessage(any(Consumer.class));
        verify(ack).acknowledge();
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonInvalidoEsDefinitivo() {
        when(sqs.sendMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("x").build()));
        procesador.procesar("notificaciones", "no es json", mensaje("no es json", 1), ack, e -> {});
        verify(sqs).sendMessage(any(Consumer.class));
        verify(ack).acknowledge();
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorTransitorioEnElUltimoIntentoVaALaDlqConCausa() {
        when(sqs.sendMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("x").build()));
        procesador.procesar("notificaciones", JSON, mensaje(JSON, 3), ack, e -> {
            throw new ErrorTransitorio("correo caído");
        });
        verify(sqs).sendMessage(any(Consumer.class));
        verify(ack).acknowledge();
        verify(duplicados, never()).registrar(anyString(), anyString());
    }
}
