package com.pedidos360.eventos.notificaciones;

import com.pedidos360.eventos.comun.ProcesadorMensajes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

/** Cola pedidos360-notificaciones-q: recibe TODOS los eventos del tópico (fanout, sin filtro). */
@Component
public class NotificacionesListener {

    private static final String CONSUMIDOR = "notificaciones";

    private final ProcesadorMensajes procesador;
    private final NotificacionService service;

    public NotificacionesListener(ProcesadorMensajes procesador, NotificacionService service) {
        this.procesador = procesador;
        this.service = service;
    }

    @SqsListener(id = "${mensajeria.consumidores.notificaciones.id}",
            queueNames = "${mensajeria.consumidores.notificaciones.cola}",
            acknowledgementMode = "MANUAL")
    public void recibir(@Payload String cuerpo,
                        @Header(SqsHeaders.SQS_SOURCE_DATA_HEADER) Message original,
                        Acknowledgement ack) {
        procesador.procesar(CONSUMIDOR, cuerpo, original, ack, service::notificar);
    }
}
