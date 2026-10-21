package com.pedidos360.eventos.auditoria;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Historial inmutable de todo lo que le pasó a cada pedido (un registro por evento). */
@Entity
@Table(name = "auditoria_eventos")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String eventoId;

    @Column(nullable = false, length = 60)
    private String tipoEvento;

    @Column(nullable = false)
    private Long pedidoId;

    private String usuarioEmail;

    private String estado;

    private Double total;

    @Column(nullable = false)
    private LocalDateTime registradoEn;

    protected RegistroAuditoria() {}

    public RegistroAuditoria(String eventoId, String tipoEvento, Long pedidoId, String usuarioEmail,
                             String estado, Double total) {
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
        this.pedidoId = pedidoId;
        this.usuarioEmail = usuarioEmail;
        this.estado = estado;
        this.total = total;
        this.registradoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEventoId() { return eventoId; }
    public String getTipoEvento() { return tipoEvento; }
    public Long getPedidoId() { return pedidoId; }
    public String getUsuarioEmail() { return usuarioEmail; }
    public String getEstado() { return estado; }
    public Double getTotal() { return total; }
    public LocalDateTime getRegistradoEn() { return registradoEn; }
}
