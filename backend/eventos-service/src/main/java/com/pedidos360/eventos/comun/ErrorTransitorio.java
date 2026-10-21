package com.pedidos360.eventos.comun;

/** Falla temporal (servicio externo caído, timeout): el mensaje NO se confirma y SQS lo reintenta. */
public class ErrorTransitorio extends RuntimeException {
    public ErrorTransitorio(String mensaje) { super(mensaje); }
}
