package br.com.ada.flashsale.checkout.api;

import br.com.ada.flashsale.checkout.persistence.OrderProjection;
import br.com.ada.flashsale.contracts.OrderStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        OrderStatus status,
        LocalDateTime dataHoraAtualizacao) {

    public static OrderResponse from(OrderProjection projection) {
        return new OrderResponse(
                projection.getPedidoId(),
                projection.getProdutoId(),
                projection.getQuantidade(),
                projection.getClienteCpf(),
                projection.getClienteNome(),
                projection.getStatus(),
                projection.getDataHoraAtualizacao());
    }
}