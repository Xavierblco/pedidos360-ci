package com.pedidos360.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Crear una cola. Si se indica dlq, se crea también la DLQ y se configura la Redrive Policy.
 * Campos opcionales: si vienen nulos, se usan los valores por defecto de SQS.
 */
public record CrearColaRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,80}", message = "Solo letras, números, guion y guion bajo (máx. 80)")
        String nombre,
        @Min(60) @Max(1209600) Integer retencionSegundos,
        @Min(0) @Max(43200) Integer visibilityTimeoutSegundos,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String dlq,
        @Min(1) @Max(1000) Integer maxReceiveCount) {}
