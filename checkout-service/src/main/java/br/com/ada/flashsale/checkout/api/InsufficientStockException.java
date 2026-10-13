package br.com.ada.flashsale.checkout.api;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productId) {
        super("Estoque insuficiente para o produto " + productId);
    }
}