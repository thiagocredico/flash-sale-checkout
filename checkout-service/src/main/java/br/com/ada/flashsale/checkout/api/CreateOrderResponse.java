package br.com.ada.flashsale.checkout.api;

import br.com.ada.flashsale.contracts.OrderStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateOrderResponse(
        UUID pedidoId,
        OrderStatus status,
        LocalDateTime dataHoraRequisicao) {
}