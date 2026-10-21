package com.pedidos360.admin.web;

import com.pedidos360.admin.config.Topologia;
import com.pedidos360.admin.service.TopologiaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/topologia")
@Tag(name = "Topología", description = "Topología declarada en código (1 tópico, 3 colas, 3 DLQ)")
public class TopologiaController {

    private final TopologiaService topologia;

    public TopologiaController(TopologiaService topologia) {
        this.topologia = topologia;
    }

    @GetMapping
    @Operation(summary = "Muestra la topología declarada (nombres, retención, filtros)")
    public Topologia declarada() {
        return topologia.declarada();
    }

    @PostMapping("/aplicar")
    @Operation(summary = "Crea o actualiza en AWS todo lo declarado (idempotente)")
    public List<String> aplicar() {
        return topologia.aplicar();
    }
}
