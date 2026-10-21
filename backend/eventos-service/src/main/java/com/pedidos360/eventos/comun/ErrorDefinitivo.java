package com.pedidos360.eventos.comun;

/** Falla permanente (datos inválidos): reintentar no sirve, el mensaje va directo a la DLQ con su causa. */
public class ErrorDefinitivo extends RuntimeException {
    public ErrorDefinitivo(String mensaje) { super(mensaje); }
}
