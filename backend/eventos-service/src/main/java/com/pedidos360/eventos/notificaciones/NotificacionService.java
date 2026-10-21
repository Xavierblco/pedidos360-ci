package com.pedidos360.eventos.notificaciones;

import com.pedidos360.eventos.comun.ErrorDefinitivo;
import com.pedidos360.eventos.comun.ErrorTransitorio;
import com.pedidos360.eventos.comun.EventoPedido;
import com.pedidos360.eventos.config.MensajeriaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dominio: avisar al cliente por correo de cada cambio en su pedido.
 * El envío de correo es simulado (se registra en el log); con
 * mensajeria.simulacion.fallo-transitorio-notificaciones=true se simula que el proveedor de correo está caído.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final MensajeriaProperties props;

    public NotificacionService(MensajeriaProperties props) {
        this.props = props;
    }

    public void notificar(EventoPedido evento) {
        if (evento.usuarioEmail() == null || !evento.usuarioEmail().contains("@")) {
            throw new ErrorDefinitivo("Pedido " + evento.pedidoId() + " sin correo de cliente válido");
        }
        if (props.simulacion().falloTransitorioNotificaciones()) {
            throw new ErrorTransitorio("Proveedor de correo no disponible (simulado)");
        }
        trabajar();
        String asunto = switch (evento.tipoEvento()) {
            case "PEDIDO_CREADO" -> "Recibimos tu pedido #" + evento.pedidoId();
            case "PEDIDO_ESTADO_ACTUALIZADO" -> "Tu pedido #" + evento.pedidoId() + " ahora está " + evento.estado();
            default -> "Novedades de tu pedido #" + evento.pedidoId();
        };
        log.info("[notificaciones] Correo enviado a {}: \"{}\"", evento.usuarioEmail(), asunto);
    }

    private void trabajar() {
        try {
            Thread.sleep(props.simulacion().demoraMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorTransitorio("Procesamiento interrumpido");
        }
    }
}
