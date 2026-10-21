package com.pedidos360.eventos.despacho;

import com.pedidos360.eventos.comun.ProcesadorMensajes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

/** Cola pedidos360-despacho-q: solo eventos con tipoEntrega = ENVIO (filter policy). */
@Component
public class DespachoListener {

    private static final String CONSUMIDOR = "despacho";

    private final ProcesadorMensajes procesador;
    private final DespachoService service;

    public DespachoListener(ProcesadorMensajes procesador, DespachoService service) {
        this.procesador = procesador;
        this.service = service;
    }

    @SqsListener(id = "${mensajeria.consumidores.despacho.id}",
            queueNames = "${mensajeria.consumidores.despacho.cola}",
            acknowledgementMode = "MANUAL")
    public void recibir(@Payload String cuerpo,
                        @Header(SqsHeaders.SQS_SOURCE_DATA_HEADER) Message original,
                        Acknowledgement ack) {
        procesador.procesar(CONSUMIDOR, cuerpo, original, ack, service::procesar);
    }
}
