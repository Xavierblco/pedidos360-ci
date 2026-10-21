package com.pedidos360.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Clase de configuración que DECLARA la topología (separada de la lógica de negocio):
 * <pre>
 *   SNS  pedidos360-eventos
 *    ├── pedidos360-notificaciones-q  (fanout)                 → DLQ pedidos360-notificaciones-dlq
 *    ├── pedidos360-despacho-q        (filtro tipoEntrega=ENVIO) → DLQ pedidos360-despacho-dlq
 *    └── pedidos360-auditoria-q       (fanout)                 → DLQ pedidos360-auditoria-dlq
 *   Redrive Policy: maxReceiveCount = 3 · Raw message delivery = true
 * </pre>
 * Los valores vienen de application.yml; aquí se validan y se arma el bean {@link Topologia}.
 */
@Configuration
public class TopologiaMensajeria {

    @Bean
    public Topologia topologia(TopologiaProperties p) {
        if (p.topico() == null || p.topico().isBlank()) {
            throw new IllegalStateException("mensajeria.topologia.topico es obligatorio");
        }
        if (p.maxReceiveCount() < 1 || p.maxReceiveCount() > 1000) {
            throw new IllegalStateException("maxReceiveCount debe estar entre 1 y 1000");
        }
        if (p.colas() == null || p.colas().isEmpty()) {
            throw new IllegalStateException("La topología debe declarar al menos una cola");
        }
        Set<String> nombres = new HashSet<>();
        List<Topologia.ColaDeclarada> colas = p.colas().stream().map(c -> {
            if (c.dlq() == null || c.dlq().isBlank()) {
                throw new IllegalStateException("La cola " + c.nombre() + " no tiene DLQ declarada");
            }
            if (!nombres.add(c.nombre()) || !nombres.add(c.dlq())) {
                throw new IllegalStateException("Nombre de cola o DLQ repetido: " + c.nombre());
            }
            validarRango("visibility-timeout-segundos", c.visibilityTimeoutSegundos(), 0, 43200);
            validarRango("retencion-segundos", c.retencionSegundos(), 60, 1209600);
            validarRango("dlq-retencion-segundos", c.dlqRetencionSegundos(), 60, 1209600);
            if (c.dlqRetencionSegundos() < c.retencionSegundos()) {
                // Si la DLQ retiene menos que la cola, un mensaje podría expirar en la DLQ antes de revisarlo.
                throw new IllegalStateException("La DLQ de " + c.nombre() + " debe retener al menos lo mismo que la cola");
            }
            String filtro = (c.filtro() == null || c.filtro().isBlank()) ? null : c.filtro().trim();
            return new Topologia.ColaDeclarada(c.nombre(), c.dlq(), c.consumidorId(), c.visibilityTimeoutSegundos(),
                    c.retencionSegundos(), c.dlqRetencionSegundos(), filtro);
        }).toList();
        return new Topologia(p.topico(), p.maxReceiveCount(), colas);
    }

    private static void validarRango(String nombre, int valor, int min, int max) {
        if (valor < min || valor > max) {
            throw new IllegalStateException(nombre + " fuera de rango (" + min + "-" + max + "): " + valor);
        }
    }
}
