package com.pedidos360.eventos.control;

import com.pedidos360.eventos.auditoria.AuditoriaService;
import com.pedidos360.eventos.auditoria.RegistroAuditoria;
import com.pedidos360.eventos.dlq.RegistroDlq;
import com.pedidos360.eventos.dlq.RegistroDlqRepository;
import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * API interna que usa mensajeria-admin para controlar los consumidores de ESTA instancia.
 * No se publica en API Gateway: solo debe ser accesible desde la red interna.
 */
@RestController
@RequestMapping("/api")
public class ConsumidoresController {

    private final MessageListenerContainerRegistry registry;
    private final RegistroDlqRepository registrosDlq;
    private final AuditoriaService auditoria;
    private final String instancia;

    public ConsumidoresController(MessageListenerContainerRegistry registry, RegistroDlqRepository registrosDlq,
                                  AuditoriaService auditoria, @Value("${server.port}") String puerto) {
        this.registry = registry;
        this.registrosDlq = registrosDlq;
        this.auditoria = auditoria;
        this.instancia = "eventos-service:" + puerto;
    }

    public record EstadoConsumidor(String id, boolean activo, String instancia) {}

    @GetMapping("/consumidores")
    public List<EstadoConsumidor> listar() {
        return registry.getListenerContainers().stream()
                .map(c -> new EstadoConsumidor(c.getId(), c.isRunning(), instancia))
                .sorted(Comparator.comparing(EstadoConsumidor::id))
                .toList();
    }

    @PostMapping("/consumidores/{id}/pausar")
    public EstadoConsumidor pausar(@PathVariable String id) {
        MessageListenerContainer<?> c = buscar(id);
        c.stop();
        return new EstadoConsumidor(id, c.isRunning(), instancia);
    }

    @PostMapping("/consumidores/{id}/reanudar")
    public EstadoConsumidor reanudar(@PathVariable String id) {
        MessageListenerContainer<?> c = buscar(id);
        c.start();
        return new EstadoConsumidor(id, c.isRunning(), instancia);
    }

    @GetMapping("/dlq/registros")
    public List<RegistroDlq> registrosDlq() {
        return registrosDlq.findTop50ByOrderByDetectadoEnDesc();
    }

    @GetMapping("/auditoria/pedidos/{pedidoId}")
    public List<RegistroAuditoria> historial(@PathVariable Long pedidoId) {
        return auditoria.historial(pedidoId);
    }

    @GetMapping("/instancia")
    public Map<String, String> instancia() {
        return Map.of("instancia", instancia);
    }

    private MessageListenerContainer<?> buscar(String id) {
        MessageListenerContainer<?> c = registry.getContainerById(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el consumidor " + id);
        }
        return c;
    }
}
