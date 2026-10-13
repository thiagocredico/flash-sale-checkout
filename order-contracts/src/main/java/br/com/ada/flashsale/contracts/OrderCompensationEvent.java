package br.com.ada.flashsale.contracts;

public record OrderCompensationEvent(
        String pedidoId,
        String produtoId,
        int quantidade,
        String motivo) {
}