package com.pedidos360.admin.web;

import com.pedidos360.admin.dto.EstadoConsumidor;
import com.pedidos360.admin.service.ConsumidorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/consumidores")
@Tag(name = "Consumidores", description = "Pausar y reanudar los listeners de eventos-service (todas las instancias)")
public class ConsumidoresController {

    private final ConsumidorService consumidores;

    public ConsumidoresController(ConsumidorService consumidores) {
        this.consumidores = consumidores;
    }

    @GetMapping
    @Operation(summary = "Estado de cada consumidor en cada instancia")
    public List<EstadoConsumidor> listar() {
        return consumidores.listar();
    }

    @PostMapping("/{id}/pausar")
    @Operation(summary = "Pausa el consumidor: los mensajes se acumulan en la cola sin perderse")
    public List<EstadoConsumidor> pausar(@PathVariable String id) {
        return consumidores.pausar(id);
    }

    @PostMapping("/{id}/reanudar")
    @Operation(summary = "Reanuda el consumidor: procesa lo acumulado")
    public List<EstadoConsumidor> reanudar(@PathVariable String id) {
        return consumidores.reanudar(id);
    }
}
