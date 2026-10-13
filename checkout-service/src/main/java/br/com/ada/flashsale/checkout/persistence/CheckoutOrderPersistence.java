package br.com.ada.flashsale.checkout.persistence;

import br.com.ada.flashsale.checkout.api.CreateOrderRequest;
import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderQueues;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CheckoutOrderPersistence {

    private final OrderProjectionRepository orderRepository;
    private final CheckoutOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CheckoutOrderPersistence(
            OrderProjectionRepository orderRepository,
            CheckoutOutboxEventRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(UUID orderId, CreateOrderRequest request, OrderCreatedEvent event, LocalDateTime now) {
        orderRepository.save(OrderProjection.processing(orderId, request, now));
        try {
            outboxRepository.save(new CheckoutOutboxEvent(
                    UUID.randomUUID(), OrderQueues.ORDER_CREATED, objectMapper.writeValueAsString(event), now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize order event", exception);
        }
    }
}