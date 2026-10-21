package com.pedidos360.admin.service;

import com.pedidos360.admin.dto.EstadoConsumidor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pausa y reanuda consumidores que viven en OTRO microservicio (eventos-service).
 * El admin no tiene acceso a los listeners: les envía la orden por HTTP a cada instancia configurada.
 */
@Service
public class ConsumidorService {

    private final RestClient http;
    private final List<String> instancias;

    public ConsumidorService(RestClient.Builder builder, @Value("${consumidores.urls}") String urls) {
        this.http = builder.build();
        this.instancias = Arrays.stream(urls.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public List<EstadoConsumidor> listar() {
        List<EstadoConsumidor> resultado = new ArrayList<>();
        for (String base : instancias) {
            try {
                List<EstadoConsumidor> lista = http.get().uri(base + "/api/consumidores").retrieve()
                        .body(new ParameterizedTypeReference<List<EstadoConsumidor>>() {});
                if (lista != null) resultado.addAll(lista);
            } catch (RestClientException e) {
                resultado.add(new EstadoConsumidor(null, false, base, "Instancia no disponible: " + e.getMessage()));
            }
        }
        return resultado;
    }

    public List<EstadoConsumidor> pausar(String id) {
        return enviar(id, "pausar");
    }

    public List<EstadoConsumidor> reanudar(String id) {
        return enviar(id, "reanudar");
    }

    private List<EstadoConsumidor> enviar(String id, String accion) {
        List<EstadoConsumidor> resultado = new ArrayList<>();
        for (String base : instancias) {
            try {
                resultado.add(http.post().uri(base + "/api/consumidores/{id}/" + accion, id).retrieve()
                        .body(EstadoConsumidor.class));
            } catch (RestClientException e) {
                resultado.add(new EstadoConsumidor(id, false, base, "No se pudo " + accion + ": " + e.getMessage()));
            }
        }
        return resultado;
    }
}
