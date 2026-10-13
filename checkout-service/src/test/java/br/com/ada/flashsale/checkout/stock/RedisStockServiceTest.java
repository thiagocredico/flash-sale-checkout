package br.com.ada.flashsale.checkout.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisStockServiceTest {

    private StringRedisTemplate redisTemplate;
    private RedisStockService stockService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        stockService = new RedisStockService(redisTemplate);
    }

    @Test
    void returnsTrueWhenRedisReservesStock() {
        doReturn(1L).when(redisTemplate).execute(
                any(RedisScript.class), eq(List.of("stock:PROD-001")), eq("2"));

        assertThat(stockService.reserve("PROD-001", 2)).isTrue();
    }

    @Test
    void returnsFalseWhenStockIsInsufficient() {
        doReturn(0L).when(redisTemplate).execute(
                any(RedisScript.class), eq(List.of("stock:PROD-001")), eq("2"));

        assertThat(stockService.reserve("PROD-001", 2)).isFalse();
    }

    @Test
    void restoresStockUsingAtomicScript() {
        doReturn(7L).when(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("stock:PROD-001", "stock-compensated:ORDER-001")),
            eq("2"), any(String.class));

        assertThat(stockService.restoreOnce("ORDER-001", "PROD-001", 2)).isTrue();
        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("stock:PROD-001", "stock-compensated:ORDER-001")),
            eq("2"), any(String.class));
        }

        @Test
        void doesNotRestoreStockTwiceForTheSameOrder() {
        doReturn(-1L).when(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("stock:PROD-001", "stock-compensated:ORDER-001")),
            eq("2"), any(String.class));

        assertThat(stockService.restoreOnce("ORDER-001", "PROD-001", 2)).isFalse();
    }

    @Test
    void rejectsInvalidReservationBeforeCallingRedis() {
        assertThatIllegalArgumentException().isThrownBy(() -> stockService.reserve("PROD-001", 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsCurrentStockValue() {
        var valueOperations = mock(ValueOperations.class);
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        doReturn("42").when(valueOperations).get("stock:PROD-001");

        assertThat(stockService.currentStock("PROD-001")).isEqualTo(42);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsZeroWhenStockKeyIsAbsent() {
        var valueOperations = mock(ValueOperations.class);
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        doReturn(null).when(valueOperations).get("stock:PROD-001");

        assertThat(stockService.currentStock("PROD-001")).isZero();
    }
}