package br.com.ada.flashsale.checkout.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import br.com.ada.flashsale.checkout.api.CreateOrderRequest;
import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderQueues;
import br.com.ada.flashsale.contracts.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutOrderPersistenceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-29T12:00:00");

    @Mock
    private OrderProjectionRepository orderRepository;

    @Mock
    private CheckoutOutboxEventRepository outboxRepository;

    @Test
    void savesProcessingProjectionAndSnakeCaseEvent() throws Exception {
        var objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        var persistence = new CheckoutOrderPersistence(orderRepository, outboxRepository, objectMapper);
        var orderId = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
        var request = new CreateOrderRequest("PROD-001", 2, "50329291076", "Fernando Augusto");
        var event = new OrderCreatedEvent(
                orderId.toString(), request.produtoId(), request.quantidade(),
                request.clienteCpf(), request.clienteNome(), NOW);

        persistence.save(orderId, request, event, NOW);

        var projectionCaptor = ArgumentCaptor.forClass(OrderProjection.class);
        verify(orderRepository).save(projectionCaptor.capture());
        assertThat(projectionCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PROCESSING);

        var outboxCaptor = ArgumentCaptor.forClass(CheckoutOutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        var outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getRoutingKey()).isEqualTo(OrderQueues.ORDER_CREATED);
        assertThat(objectMapper.readTree(outboxEvent.getPayload()).get("pedido_id").asText())
                .isEqualTo(orderId.toString());
        assertThat(objectMapper.readTree(outboxEvent.getPayload()).get("produto_id").asText())
                .isEqualTo("PROD-001");
    }
}