package com.pedidos360.admin.web;

import com.pedidos360.admin.dto.PublicarRequest;
import com.pedidos360.admin.dto.Suscripcion;
import com.pedidos360.admin.dto.SuscribirColaRequest;
import com.pedidos360.admin.dto.Topico;
import com.pedidos360.admin.service.TopicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Tópicos y suscripciones", description = "Tópicos SNS, suscripciones de colas (raw + filtros) y publicación de prueba")
public class TopicosController {

    private final TopicoService topicos;

    public TopicosController(TopicoService topicos) {
        this.topicos = topicos;
    }

    @GetMapping("/topicos")
    @Operation(summary = "Lista los tópicos SNS de la cuenta")
    public List<Topico> listar() {
        return topicos.listar();
    }

    @PostMapping("/topicos/{nombre}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un tópico (idempotente)")
    public Topico crear(@PathVariable String nombre) {
        return topicos.crear(nombre);
    }

    @DeleteMapping("/topicos/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un tópico y sus suscripciones")
    public void eliminar(@PathVariable String nombre) {
        topicos.eliminar(nombre);
    }

    @GetMapping("/topicos/{nombre}/suscripciones")
    @Operation(summary = "Suscripciones del tópico con su filter policy y raw message delivery")
    public List<Suscripcion> suscripciones(@PathVariable String nombre) {
        return topicos.suscripciones(nombre);
    }

    @PostMapping("/topicos/{nombre}/suscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Suscribe una cola al tópico (crea la policy de la cola, raw delivery y filtro opcional)")
    public Suscripcion suscribir(@PathVariable String nombre, @Valid @RequestBody SuscribirColaRequest request) {
        boolean raw = request.rawMessageDelivery() == null || request.rawMessageDelivery();
        return topicos.suscribirCola(nombre, request.cola(), request.filtro(), raw);
    }

    @DeleteMapping("/suscripciones")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una suscripción por su ARN")
    public void desuscribir(@RequestParam String arn) {
        topicos.desuscribir(arn);
    }

    @PostMapping("/topicos/{nombre}/publicar")
    @Operation(summary = "Publica un mensaje de prueba con atributos (para demostrar filtros y errores)")
    public Map<String, String> publicar(@PathVariable String nombre, @Valid @RequestBody PublicarRequest request) {
        String id = topicos.publicar(nombre, request.cuerpo().toString(), request.atributos());
        return Map.of("topico", nombre, "messageId", id);
    }
}
