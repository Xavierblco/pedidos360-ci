package com.pedidos360.eventos.comun;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Registro de idempotencia en base de datos. A diferencia de un Set en memoria, lo comparten todas
 * las instancias del servicio, así que un mensaje duplicado se detecta aunque lo reciba otra instancia.
 */
@Entity
@Table(name = "mensajes_procesados",
        uniqueConstraints = @UniqueConstraint(columnNames = {"consumidor", "eventoId"}))
public class MensajeProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String consumidor;

    @Column(nullable = false, length = 80)
    private String eventoId;

    @Column(nullable = false)
    private LocalDateTime procesadoEn;

    protected MensajeProcesado() {}

    public MensajeProcesado(String consumidor, String eventoId) {
        this.consumidor = consumidor;
        this.eventoId = eventoId;
        this.procesadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getConsumidor() { return consumidor; }
    public String getEventoId() { return eventoId; }
    public LocalDateTime getProcesadoEn() { return procesadoEn; }
}
