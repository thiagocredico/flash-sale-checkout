package br.com.ada.flashsale.checkout.api;

public class OrderSubmissionException extends RuntimeException {

    public OrderSubmissionException(Throwable cause) {
        super("Não foi possível registrar o pedido", cause);
    }
}