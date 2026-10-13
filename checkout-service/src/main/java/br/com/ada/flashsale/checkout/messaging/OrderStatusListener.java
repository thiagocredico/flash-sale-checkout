package br.com.ada.flashsale.checkout.messaging;

import br.com.ada.flashsale.checkout.cache.RedisOrderCache;
import br.com.ada.flashsale.checkout.persistence.OrderProjectionRepository;
import br.com.ada.flashsale.contracts.OrderQueues;
import br.com.ada.flashsale.contracts.OrderStatusUpdatedEvent;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderStatusListener {

    private final OrderProjectionRepository orderRepository;
    private final RedisOrderCache orderCache;

    public OrderStatusListener(OrderProjectionRepository orderRepository, RedisOrderCache orderCache) {
        this.orderRepository = orderRepository;
        this.orderCache = orderCache;
    }

    @RabbitListener(queues = OrderQueues.ORDER_STATUS_UPDATED)
    @Transactional
    public void onOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        var orderId = UUID.fromString(event.pedidoId());
        var projection = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order projection not found: " + orderId));
        projection.applyStatus(event);
        try {
            orderCache.evict(orderId);
        } catch (DataAccessException ignored) {
        }
    }
}