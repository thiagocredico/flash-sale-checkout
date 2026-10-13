package br.com.ada.flashsale.processor.service;

import br.com.ada.flashsale.contracts.OrderCompensationEvent;
import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderQueues;
import br.com.ada.flashsale.contracts.OrderStatusUpdatedEvent;
import br.com.ada.flashsale.processor.domain.Order;
import br.com.ada.flashsale.processor.persistence.OrderRepository;
import br.com.ada.flashsale.processor.persistence.OutboxEvent;
import br.com.ada.flashsale.processor.persistence.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProcessorService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean simulateFailure;

    public OrderProcessorService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${order.processor.simulate-failure:false}") boolean simulateFailure) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.simulateFailure = simulateFailure;
    }

    @Transactional
    public void process(OrderCreatedEvent event) {
        UUID orderId = UUID.fromString(event.pedidoId());
        if (orderRepository.existsById(orderId)) {
            return;
        }

        Order order = Order.from(event);
        if (simulateFailure) {
            order.cancel();
        } else {
            order.confirm();
        }
        orderRepository.save(order);

        LocalDateTime now = LocalDateTime.now(clock);
        var statusEvent = new OrderStatusUpdatedEvent(
                event.pedidoId(), order.getStatus(), order.getDataHoraAtualizacao());
        addOutboxEvent(UUID.randomUUID(), OrderQueues.ORDER_STATUS_UPDATED, statusEvent, now);

        if (simulateFailure) {
            var compensationEvent = new OrderCompensationEvent(
                    event.pedidoId(), event.produtoId(), event.quantidade(), "Falha no processamento do pedido");
            addOutboxEvent(UUID.randomUUID(), OrderQueues.ORDER_COMPENSATION, compensationEvent, now);
        }
    }

    private void addOutboxEvent(UUID eventId, String routingKey, Object event, LocalDateTime createdAt) {
        try {
            outboxRepository.save(new OutboxEvent(
                    eventId, routingKey, objectMapper.writeValueAsString(event), createdAt));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outgoing order event", exception);
        }
    }
}