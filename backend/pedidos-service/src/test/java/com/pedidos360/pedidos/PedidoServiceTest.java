package com.pedidos360.pedidos;

import com.pedidos360.pedidos.client.ProductosClient;
import com.pedidos360.pedidos.dto.PedidoDTO;
import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.dto.ProductoDTO;
import com.pedidos360.pedidos.entity.EstadoPedido;
import com.pedidos360.pedidos.exception.ResourceNotFoundException;
import com.pedidos360.pedidos.repository.PedidoRepository;
import com.pedidos360.pedidos.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PedidoServiceTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @MockBean
    private ProductosClient productosClient;

    /**
     * NimbusJwtDecoder.withIssuerLocation(...).build() descarga la configuración del issuer al crear el bean.
     * En pruebas no hay User Pool real, así que se reemplaza por un mock para que el contexto arranque.
     */
    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void autenticarUsuarioDePrueba() {
        Jwt jwt = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "RS256")
                .claim("email", "cliente@pedidos360.cl")
                .claim("sub", "usuario-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void crearPedidoConsultaYDescuentaStockEnProductosService() {
        ProductoDTO producto = new ProductoDTO();
        producto.setId(1L);
        producto.setNombre("Polera Pedidos360");
        producto.setPrecio(9990.0);
        producto.setStock(10);

        when(productosClient.obtenerProducto(1L)).thenReturn(producto);
        when(productosClient.reservarStock(1L, 2)).thenReturn(producto);

        PedidoRequest request = new PedidoRequest();
        request.setProductoId(1L);
        request.setCantidad(2);

        PedidoDTO creado = pedidoService.crear(request);

        assertNotNull(creado.getId());
        assertEquals("cliente@pedidos360.cl", creado.getUsuarioEmail());
        assertEquals(19980.0, creado.getTotal());
        assertEquals(EstadoPedido.PENDIENTE, creado.getEstado());
    }

    @Test
    void obtenerPedidoInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.obtener(999_999L));
    }

    @Test
    void actualizarEstadoCambiaElEstadoDelPedido() {
        ProductoDTO producto = new ProductoDTO();
        producto.setId(2L);
        producto.setPrecio(4990.0);
        producto.setStock(5);
        when(productosClient.obtenerProducto(2L)).thenReturn(producto);
        when(productosClient.reservarStock(2L, 1)).thenReturn(producto);

        PedidoRequest request = new PedidoRequest();
        request.setProductoId(2L);
        request.setCantidad(1);
        PedidoDTO creado = pedidoService.crear(request);

        PedidoDTO actualizado = pedidoService.actualizarEstado(creado.getId(), EstadoPedido.CONFIRMADO);
        assertEquals(EstadoPedido.CONFIRMADO, actualizado.getEstado());
    }
}
