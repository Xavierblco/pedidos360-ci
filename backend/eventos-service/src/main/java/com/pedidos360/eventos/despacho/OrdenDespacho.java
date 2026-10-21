package com.pedidos360.eventos.despacho;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Orden de despacho a domicilio. Solo existe para pedidos con tipoEntrega = ENVIO. */
@Entity
@Table(name = "ordenes_despacho")
public class OrdenDespacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long pedidoId;

    @Column(nullable = false, length = 500)
    private String direccion;

    @Column(nullable = false, length = 40)
    private String estado;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    protected OrdenDespacho() {}

    public OrdenDespacho(Long pedidoId, String direccion) {
        this.pedidoId = pedidoId;
        this.direccion = direccion;
        this.estado = "POR_PREPARAR";
        this.actualizadoEn = LocalDateTime.now();
    }

    public void cambiarEstado(String nuevo) {
        this.estado = nuevo;
        this.actualizadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public String getDireccion() { return direccion; }
    public String getEstado() { return estado; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
