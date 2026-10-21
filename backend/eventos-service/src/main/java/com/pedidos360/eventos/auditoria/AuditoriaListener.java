package com.pedidos360.eventos.auditoria;

import com.pedidos360.eventos.comun.ProcesadorMensajes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

/** Cola pedidos360-auditoria-q: recibe TODOS los eventos del tópico (fanout, sin filtro). */
@Component
public class AuditoriaListener {

    private static final String CONSUMIDOR = "auditoria";

    private final ProcesadorMensajes procesador;
    private final AuditoriaService service;

    public AuditoriaListener(ProcesadorMensajes procesador, AuditoriaService service) {
        this.procesador = procesador;
        this.service = service;
    }

    @SqsListener(id = "${mensajeria.consumidores.auditoria.id}",
            queueNames = "${mensajeria.consumidores.auditoria.cola}",
            acknowledgementMode = "MANUAL")
    public void recibir(@Payload String cuerpo,
                        @Header(SqsHeaders.SQS_SOURCE_DATA_HEADER) Message original,
                        Acknowledgement ack) {
        procesador.procesar(CONSUMIDOR, cuerpo, original, ack, service::registrar);
    }
}
