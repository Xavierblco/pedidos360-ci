package com.pedidos360.admin.dto;

/** Estado de un listener @SqsListener en una instancia de eventos-service. */
public record EstadoConsumidor(String id, boolean activo, String instancia, String error) {}
