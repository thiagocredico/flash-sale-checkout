package br.com.ada.flashsale.checkout.api;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Pedido não encontrado");
    }
}