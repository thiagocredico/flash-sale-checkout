package br.com.ada.flashsale.checkout.messaging;

import br.com.ada.flashsale.checkout.stock.RedisStockService;
import br.com.ada.flashsale.contracts.OrderCompensationEvent;
import br.com.ada.flashsale.contracts.OrderQueues;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCompensationListener {

    private final RedisStockService stockService;

    public OrderCompensationListener(RedisStockService stockService) {
        this.stockService = stockService;
    }

    @RabbitListener(queues = OrderQueues.ORDER_COMPENSATION)
    public void onCompensation(OrderCompensationEvent event) {
        stockService.restoreOnce(event.pedidoId(), event.produtoId(), event.quantidade());
    }
}