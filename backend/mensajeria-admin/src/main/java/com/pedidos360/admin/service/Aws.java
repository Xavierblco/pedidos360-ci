package com.pedidos360.admin.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Utilidad: espera una llamada asíncrona del SDK y desenvuelve la excepción real de AWS. */
final class Aws {

    private Aws() {}

    static <T> T esperar(CompletableFuture<T> futuro) {
        try {
            return futuro.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    static String nombreDesdeArn(String arn) {
        return arn == null ? null : arn.substring(arn.lastIndexOf(':') + 1);
    }
}
