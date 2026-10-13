package br.com.ada.flashsale.contracts;

import java.time.LocalDateTime;

public record OrderStatusUpdatedEvent(
        String pedidoId,
        OrderStatus status,
        LocalDateTime dataHoraAtualizacao) {
}