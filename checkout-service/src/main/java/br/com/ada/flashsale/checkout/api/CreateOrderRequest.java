package br.com.ada.flashsale.checkout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotBlank String produtoId,
        @Positive int quantidade,
        @NotBlank String clienteCpf,
        @NotBlank String clienteNome) {
}