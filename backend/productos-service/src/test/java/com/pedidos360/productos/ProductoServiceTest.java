package com.pedidos360.productos;

import com.pedidos360.productos.dto.ProductoDTO;
import com.pedidos360.productos.dto.ProductoRequest;
import com.pedidos360.productos.exception.InsufficientStockException;
import com.pedidos360.productos.exception.ResourceNotFoundException;
import com.pedidos360.productos.repository.ProductoRepository;
import com.pedidos360.productos.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProductoServiceTest {

    /**
     * NimbusJwtDecoder.withIssuerLocation(...).build() descarga la configuración del issuer al crear el bean.
     * En pruebas no hay User Pool real, así que se reemplaza por un mock para que el contexto arranque.
     */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ProductoService service;

    @Autowired
    private ProductoRepository repository;

    @Test
    void crearYObtenerProducto() {
        ProductoRequest req = new ProductoRequest();
        req.setNombre("Polera Pedidos360");
        req.setDescripcion("Polera de algodón");
        req.setPrecio(9990.0);
        req.setStock(10);

        ProductoDTO creado = service.crear(req);
        assertNotNull(creado.getId());

        ProductoDTO obtenido = service.obtener(creado.getId());
        assertEquals("Polera Pedidos360", obtenido.getNombre());
        assertEquals(10, obtenido.getStock());
    }

    @Test
    void obtenerProductoInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtener(999_999L));
    }

    @Test
    void reservarStockSuperiorAlDisponibleLanzaExcepcion() {
        ProductoRequest req = new ProductoRequest();
        req.setNombre("Taza Pedidos360");
        req.setDescripcion("Taza cerámica");
        req.setPrecio(4990.0);
        req.setStock(2);
        ProductoDTO creado = service.crear(req);

        assertThrows(InsufficientStockException.class,
                () -> service.verificarYDescontarStock(creado.getId(), 5));
    }

    @Test
    void reservarStockValidoDescuentaCorrectamente() {
        ProductoRequest req = new ProductoRequest();
        req.setNombre("Mouse Pedidos360");
        req.setDescripcion("Mouse inalámbrico");
        req.setPrecio(14990.0);
        req.setStock(5);
        ProductoDTO creado = service.crear(req);

        ProductoDTO actualizado = service.verificarYDescontarStock(creado.getId(), 3);
        assertEquals(2, actualizado.getStock());
    }
}
