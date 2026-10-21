package com.pedidos360.eventos.dlq;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Bitácora de mensajes que llegaron a alguna DLQ: id, intento y causa. */
@Entity
@Table(name = "registros_dlq")
public class RegistroDlq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String messageId;

    @Column(nullable = false, length = 120)
    private String dlq;

    @Column(length = 80)
    private String eventoId;

    private Long pedidoId;

    @Column(length = 20)
    private String intento;

    @Column(length = 500)
    private String causa;

    @Column(length = 4000)
    private String cuerpo;

    @Column(nullable = false)
    private LocalDateTime detectadoEn;

    protected RegistroDlq() {}

    public RegistroDlq(String messageId, String dlq, String eventoId, Long pedidoId, String intento,
                       String causa, String cuerpo) {
        this.messageId = messageId;
        this.dlq = dlq;
        this.eventoId = eventoId;
        this.pedidoId = pedidoId;
        this.intento = intento;
        this.causa = causa;
        this.cuerpo = cuerpo != null && cuerpo.length() > 4000 ? cuerpo.substring(0, 4000) : cuerpo;
        this.detectadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getDlq() { return dlq; }
    public String getEventoId() { return eventoId; }
    public Long getPedidoId() { return pedidoId; }
    public String getIntento() { return intento; }
    public String getCausa() { return causa; }
    public String getCuerpo() { return cuerpo; }
    public LocalDateTime getDetectadoEn() { return detectadoEn; }
}
