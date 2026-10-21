package com.pedidos360.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Publica un mensaje de prueba en un tópico (para demostrar filtros y errores definitivos).
 * @param cuerpo JSON del evento.
 * @param atributos atributos del mensaje (los que evalúan las filter policies), ej. {"tipoEntrega":"ENVIO"}.
 */
public record PublicarRequest(@NotNull JsonNode cuerpo, Map<String, String> atributos) {}
