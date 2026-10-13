package br.com.ada.flashsale.checkout.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ada.flashsale.checkout.persistence.CheckoutOrderPersistence;
import br.com.ada.flashsale.checkout.stock.RedisStockService;
import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RedisStockService stockService;

    @Mock
    private CheckoutOrderPersistence orderPersistence;

    private CreateOrderService createOrderService;

    @BeforeEach
    void configureClock() {
        createOrderService = new CreateOrderService(stockService, orderPersistence, CLOCK);
    }

    @Test
    void reservesAndPersistsOrder() {
        when(stockService.reserve("PROD-001", 2)).thenReturn(true);
        var response = createOrderService.create(validRequest());

        assertThat(response.pedidoId()).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(response.dataHoraRequisicao()).isEqualTo(LocalDateTime.of(2026, 9, 29, 12, 0));

        verify(orderPersistence).save(
                eq(response.pedidoId()),
                eq(validRequest()),
                any(OrderCreatedEvent.class),
                eq(response.dataHoraRequisicao()));
    }

    @Test
    void rejectsInsufficientStockWithoutPersistingOrder() {
        when(stockService.reserve("PROD-001", 2)).thenReturn(false);

        assertThatThrownBy(() -> createOrderService.create(validRequest()))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderPersistence, never()).save(
            any(UUID.class), any(CreateOrderRequest.class), any(OrderCreatedEvent.class), any(LocalDateTime.class));
    }

    @Test
    void restoresStockWhenOrderPersistenceFails() {
        when(stockService.reserve("PROD-001", 2)).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable"))
                .when(orderPersistence)
                .save(any(UUID.class), any(CreateOrderRequest.class), any(OrderCreatedEvent.class), any(LocalDateTime.class));

        assertThatThrownBy(() -> createOrderService.create(validRequest()))
                .isInstanceOf(OrderSubmissionException.class);

        verify(stockService).restoreOnce(any(String.class), eq("PROD-001"), eq(2));
    }

    private CreateOrderRequest validRequest() {
        return new CreateOrderRequest("PROD-001", 2, "50329291076", "Fernando Augusto");
    }

}