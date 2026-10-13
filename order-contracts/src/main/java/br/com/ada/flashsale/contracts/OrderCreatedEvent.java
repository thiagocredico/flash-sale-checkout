package br.com.ada.flashsale.contracts;

import java.time.LocalDateTime;

public record OrderCreatedEvent(
        String pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        LocalDateTime dataHoraCriacao) {
}