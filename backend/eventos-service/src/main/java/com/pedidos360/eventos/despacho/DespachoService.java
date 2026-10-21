package com.pedidos360.eventos.despacho;

import com.pedidos360.eventos.comun.ErrorDefinitivo;
import com.pedidos360.eventos.comun.ErrorTransitorio;
import com.pedidos360.eventos.comun.EventoPedido;
import com.pedidos360.eventos.config.MensajeriaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dominio: logística de envío a domicilio. Gracias a la filter policy {"tipoEntrega":["ENVIO"]},
 * esta cola nunca recibe pedidos con retiro en tienda.
 */
@Service
public class DespachoService {

    private static final Logger log = LoggerFactory.getLogger(DespachoService.class);

    private final OrdenDespachoRepository repository;
    private final MensajeriaProperties props;

    public DespachoService(OrdenDespachoRepository repository, MensajeriaProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public void procesar(EventoPedido evento) {
        if (!"ENVIO".equals(evento.tipoEntrega())) {
            throw new ErrorDefinitivo("Despacho recibió un pedido con tipoEntrega=" + evento.tipoEntrega()
                    + " (revisar la filter policy de la suscripción)");
        }
        if (evento.direccion() == null || evento.direccion().isBlank()) {
            throw new ErrorDefinitivo("Pedido " + evento.pedidoId() + " con envío pero sin dirección");
        }
        try {
            Thread.sleep(props.simulacion().demoraMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorTransitorio("Procesamiento interrumpido");
        }

        OrdenDespacho orden = repository.findByPedidoId(evento.pedidoId())
                .orElseGet(() -> new OrdenDespacho(evento.pedidoId(), evento.direccion()));
        String estadoDespacho = switch (evento.estado() == null ? "" : evento.estado()) {
            case "CONFIRMADO", "PREPARANDO" -> "EN_PREPARACION";
            case "LISTO" -> "LISTO_PARA_DESPACHO";
            case "CANCELADO" -> "CANCELADO";
            default -> "POR_PREPARAR";
        };
        orden.cambiarEstado(estadoDespacho);
        repository.save(orden);
        log.info("[despacho] Pedido {} -> orden de despacho {} ({})", evento.pedidoId(), estadoDespacho, evento.direccion());
    }
}
