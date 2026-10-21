package com.pedidos360.admin.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopologiaMensajeriaTest {

    private static TopologiaProperties.Cola cola(String nombre, int retencion, int dlqRetencion, String filtro) {
        return new TopologiaProperties.Cola(nombre, nombre.replace("-q", "-dlq"), "c-" + nombre, 15,
                retencion, dlqRetencion, filtro);
    }

    @Test
    void declaraTresColasConSuDlqYUnFiltro() {
        TopologiaProperties p = new TopologiaProperties(true, "pedidos360-eventos", 3, List.of(
                cola("pedidos360-notificaciones-q", 86400, 1209600, ""),
                cola("pedidos360-despacho-q", 345600, 1209600, "{\"tipoEntrega\":[\"ENVIO\"]}"),
                cola("pedidos360-auditoria-q", 604800, 1209600, null)));

        Topologia t = new TopologiaMensajeria().topologia(p);

        assertEquals(3, t.colas().size());
        assertEquals(1, t.colas().stream().filter(Topologia.ColaDeclarada::tieneFiltro).count());
        assertEquals("pedidos360-despacho-dlq", t.cola("pedidos360-despacho-q").dlq());
    }

    @Test
    void rechazaDlqQueRetieneMenosQueLaCola() {
        TopologiaProperties p = new TopologiaProperties(true, "t", 3,
                List.of(cola("a-q", 604800, 86400, null)));
        assertThrows(IllegalStateException.class, () -> new TopologiaMensajeria().topologia(p));
    }

    @Test
    void rechazaColaSinDlq() {
        TopologiaProperties p = new TopologiaProperties(true, "t", 3,
                List.of(new TopologiaProperties.Cola("a-q", "", "c", 15, 86400, 1209600, null)));
        assertThrows(IllegalStateException.class, () -> new TopologiaMensajeria().topologia(p));
    }
}
