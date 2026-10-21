package com.pedidos360.pedidos.service;

import com.pedidos360.pedidos.client.ProductosClient;
import com.pedidos360.pedidos.dto.PedidoDTO;
import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.dto.ProductoDTO;
import com.pedidos360.pedidos.entity.EstadoPedido;
import com.pedidos360.pedidos.entity.Pedido;
import com.pedidos360.pedidos.entity.PedidoItem;
import com.pedidos360.pedidos.exception.ResourceNotFoundException;
import com.pedidos360.pedidos.messaging.EventoPedido;
import com.pedidos360.pedidos.messaging.EventoPublisher;
import com.pedidos360.pedidos.messaging.TipoEvento;
import com.pedidos360.pedidos.repository.PedidoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoService {

    private final PedidoRepository repository;
    private final ProductosClient productosClient;
    private final EventoPublisher eventoPublisher;

    public PedidoService(PedidoRepository repository, ProductosClient productosClient, EventoPublisher eventoPublisher) {
        this.repository = repository;
        this.productosClient = productosClient;
        this.eventoPublisher = eventoPublisher;
    }

    public List<PedidoDTO> listar() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    public PedidoDTO obtener(Long id) {
        Pedido pedido = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con id " + id));
        return toDTO(pedido);
    }

    public PedidoDTO crear(PedidoRequest request) {
        if ("ENVIO".equals(request.getTipoEntrega()) && (request.getDireccion() == null || request.getDireccion().trim().length() < 8 ||
                !request.getDireccion().trim().matches("(?i).*[\\p{L}].*\\d.*|.*\\d.*[\\p{L}].*"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La dirección debe incluir calle y número");
        }
        if ("RETIRO".equals(request.getTipoEntrega()) && request.getDireccion() != null && !request.getDireccion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El retiro no requiere dirección");
        }
        List<ProductoDTO> productos = new ArrayList<>();
        for (PedidoRequest.Item item : request.getItems()) productos.add(productosClient.obtenerProducto(item.getProductoId()));
        for (int i = 0; i < request.getItems().size(); i++) {
            PedidoRequest.Item item = request.getItems().get(i);
            if (productos.get(i).getPrecio() == null || productos.get(i).getPrecio() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Precio de producto inválido");
            productosClient.reservarStock(item.getProductoId(), item.getCantidad());
        }
        Pedido pedido = new Pedido();
        pedido.setUsuarioEmail(emailUsuarioActual());
        pedido.setTipoEntrega(request.getTipoEntrega());
        pedido.setDireccion("ENVIO".equals(request.getTipoEntrega()) ? request.getDireccion().trim() : null);
        List<PedidoItem> lineas = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < request.getItems().size(); i++) {
            PedidoRequest.Item item = request.getItems().get(i);
            ProductoDTO producto = productos.get(i);
            lineas.add(new PedidoItem(producto.getId(), producto.getNombre(), item.getCantidad(), producto.getPrecio()));
            total += producto.getPrecio() * item.getCantidad();
        }
        PedidoItem primero = lineas.get(0);
        pedido.setItems(lineas);
        pedido.setProductoId(primero.getProductoId());
        pedido.setCantidad(request.getItems().stream().mapToInt(PedidoRequest.Item::getCantidad).sum());
        pedido.setPrecioUnitario(primero.getPrecioUnitario());
        pedido.setTotal(total);
        pedido.setEstado(EstadoPedido.PENDIENTE);

        Pedido guardado = repository.save(pedido);
        // Parcial 2: notificaciones, despacho y auditoría se procesan en segundo plano (SNS → SQS).
        eventoPublisher.publicar(EventoPedido.de(guardado, TipoEvento.PEDIDO_CREADO));
        return toDTO(guardado);
    }

    public PedidoDTO actualizarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con id " + id));
        pedido.setEstado(nuevoEstado);
        Pedido guardado = repository.save(pedido);
        eventoPublisher.publicar(EventoPedido.de(guardado, TipoEvento.PEDIDO_ESTADO_ACTUALIZADO));
        return toDTO(guardado);
    }

    private String emailUsuarioActual() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            return email != null ? email : jwt.getSubject();
        }
        return "desconocido";
    }

    private PedidoDTO toDTO(Pedido p) {
        PedidoDTO dto = new PedidoDTO(p.getId(), p.getUsuarioEmail(), p.getProductoId(), p.getCantidad(),
                p.getPrecioUnitario(), p.getTotal(), p.getEstado(), p.getFechaCreacion());
        dto.setItems(p.getItems().stream().map(i -> new PedidoDTO.Item(i.getProductoId(), i.getNombre(), i.getCantidad(), i.getPrecioUnitario())).toList());
        dto.setTipoEntrega(p.getTipoEntrega());
        dto.setDireccion(p.getDireccion());
        return dto;
    }
}
