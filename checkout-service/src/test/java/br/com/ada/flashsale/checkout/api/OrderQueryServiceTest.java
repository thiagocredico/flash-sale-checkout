package br.com.ada.flashsale.checkout.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ada.flashsale.checkout.cache.RedisOrderCache;
import br.com.ada.flashsale.checkout.persistence.OrderProjection;
import br.com.ada.flashsale.checkout.persistence.OrderProjectionRepository;
import br.com.ada.flashsale.contracts.OrderStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
    private static final LocalDateTime UPDATED_AT = LocalDateTime.parse("2026-09-29T12:04:10");

    @Mock
    private RedisOrderCache orderCache;

    @Mock
    private OrderProjectionRepository orderRepository;

    @Test
    void returnsCacheHitWithoutQueryingDatabase() {
        var cachedOrder = new OrderResponse(
                ORDER_ID, "PROD-001", 2, "50329291076", "Fernando Augusto", OrderStatus.CONFIRMED, UPDATED_AT);
        when(orderCache.find(ORDER_ID)).thenReturn(Optional.of(cachedOrder));

        var response = service().find(ORDER_ID);

        assertThat(response).isEqualTo(cachedOrder);
        verify(orderRepository, never()).findById(ORDER_ID);
    }

    @Test
    void loadsDatabaseAndFillsCacheOnCacheMiss() {
        when(orderCache.find(ORDER_ID)).thenReturn(Optional.empty());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(projection()));

        var response = service().find(ORDER_ID);

        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderCache).put(response);
    }

    @Test
    void retriesThreeTimesBeforeReturningNotFound() {
        when(orderCache.find(ORDER_ID)).thenReturn(Optional.empty());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().find(ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, times(3)).findById(ORDER_ID);
    }

    private OrderQueryService service() {
        return new OrderQueryService(orderCache, orderRepository, 0);
    }

    private OrderProjection projection() {
        return OrderProjection.processing(
                ORDER_ID,
                new CreateOrderRequest("PROD-001", 2, "50329291076", "Fernando Augusto"),
                LocalDateTime.parse("2026-09-29T12:00:00"));
    }
}