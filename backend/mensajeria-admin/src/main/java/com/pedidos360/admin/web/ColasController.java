package com.pedidos360.admin.web;

import com.pedidos360.admin.dto.CrearColaRequest;
import com.pedidos360.admin.dto.EstadoCola;
import com.pedidos360.admin.dto.ResultadoReproceso;
import com.pedidos360.admin.service.ColaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/colas")
@Tag(name = "Colas", description = "Estado, creación, eliminación, purga y DLQ de colas SQS")
public class ColasController {

    private final ColaService colas;

    public ColasController(ColaService colas) {
        this.colas = colas;
    }

    @GetMapping
    @Operation(summary = "Lista colas con su estado (disponibles, en proceso, DLQ). Filtro opcional por prefijo")
    public List<EstadoCola> listar(@RequestParam(defaultValue = "pedidos360") String prefijo) {
        return colas.listar(prefijo);
    }

    @GetMapping("/{nombre}")
    @Operation(summary = "Estado y configuración de una cola")
    public EstadoCola estado(@PathVariable String nombre) {
        return colas.estado(nombre);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cola (y opcionalmente su DLQ con Redrive Policy)")
    public EstadoCola crear(@Valid @RequestBody CrearColaRequest request) {
        return colas.crear(request);
    }

    @DeleteMapping("/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una cola (AWS no permite recrear el mismo nombre durante 60 s)")
    public void eliminar(@PathVariable String nombre) {
        colas.eliminar(nombre);
    }

    @PostMapping("/{nombre}/purgar")
    @Operation(summary = "Borra todos los mensajes de la cola (máximo una purga cada 60 s)")
    public Map<String, String> purgar(@PathVariable String nombre) {
        colas.purgar(nombre);
        return Map.of("cola", nombre, "resultado", "purgada");
    }

    @PostMapping("/{nombre}/dlq/reprocesar")
    @Operation(summary = "Devuelve los mensajes de la DLQ de esta cola a la cola, conservando sus atributos")
    public ResultadoReproceso reprocesarDlq(@PathVariable String nombre) {
        return colas.reprocesarDlq(nombre);
    }

    @PostMapping("/{nombre}/dlq/purgar")
    @Operation(summary = "Purga la DLQ de esta cola (después de diagnosticar los mensajes)")
    public Map<String, String> purgarDlq(@PathVariable String nombre) {
        return Map.of("cola", nombre, "dlqPurgada", colas.purgarDlq(nombre));
    }
}
