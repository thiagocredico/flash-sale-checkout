package br.com.ada.flashsale.processor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderQueues;
import br.com.ada.flashsale.contracts.OrderStatus;
import br.com.ada.flashsale.processor.domain.Order;
import br.com.ada.flashsale.processor.persistence.OrderRepository;
import br.com.ada.flashsale.processor.persistence.OutboxEvent;
import br.com.ada.flashsale.processor.persistence.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderProcessorServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void confirmsOrderAndWritesStatusEventToOutbox() throws Exception {
        var service = service(false);
        when(orderRepository.existsById(ORDER_ID)).thenReturn(false);

        service.process(orderCreatedEvent());

        var orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        var outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        var event = outboxCaptor.getValue();
        assertThat(event.getRoutingKey()).isEqualTo(OrderQueues.ORDER_STATUS_UPDATED);
        assertThat(objectMapper.readTree(event.getPayload()).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(objectMapper.readTree(event.getPayload()).has("pedido_id")).isTrue();
    }

    @Test
    void cancellationWritesStatusAndCompensationEvents() throws Exception {
        var service = service(true);
        when(orderRepository.existsById(ORDER_ID)).thenReturn(false);

        service.process(orderCreatedEvent());

        var orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);

        var outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, org.mockito.Mockito.times(2)).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues())
                .extracting(OutboxEvent::getRoutingKey)
                .containsExactlyInAnyOrder(OrderQueues.ORDER_STATUS_UPDATED, OrderQueues.ORDER_COMPENSATION);
        var compensation = outboxCaptor.getAllValues().stream()
                .filter(event -> event.getRoutingKey().equals(OrderQueues.ORDER_COMPENSATION))
                .findFirst()
                .orElseThrow();
        assertThat(objectMapper.readTree(compensation.getPayload()).get("produto_id").asText())
                .isEqualTo("PROD-001");
    }

    @Test
    void ignoresRedeliveredOrderAlreadyPersisted() {
        var service = service(false);
        when(orderRepository.existsById(ORDER_ID)).thenReturn(true);

        service.process(orderCreatedEvent());

        verify(orderRepository, never()).save(any(Order.class));
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    private OrderProcessorService service(boolean simulateFailure) {
        return new OrderProcessorService(orderRepository, outboxRepository, objectMapper, CLOCK, simulateFailure);
    }

    private OrderCreatedEvent orderCreatedEvent() {
        return new OrderCreatedEvent(
                ORDER_ID.toString(),
                "PROD-001",
                2,
                "50329291076",
                "Fernando Augusto",
                java.time.LocalDateTime.parse("2026-09-29T12:00:00"));
    }
}