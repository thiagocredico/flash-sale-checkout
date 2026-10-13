package br.com.ada.flashsale.processor.messaging;

import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderQueues;
import br.com.ada.flashsale.processor.service.OrderProcessorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private final OrderProcessorService orderProcessorService;

    public OrderCreatedListener(OrderProcessorService orderProcessorService) {
        this.orderProcessorService = orderProcessorService;
    }

    @RabbitListener(queues = OrderQueues.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        orderProcessorService.process(event);
    }
}