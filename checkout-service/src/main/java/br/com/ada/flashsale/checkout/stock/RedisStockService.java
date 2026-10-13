package br.com.ada.flashsale.checkout.stock;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisStockService {

    private static final String STOCK_KEY_PREFIX = "stock:";
    // 30 days comfortably outlives any realistic RabbitMQ redelivery window while bounding marker growth.
    private static final long COMPENSATION_MARKER_TTL_SECONDS = 30L * 24 * 60 * 60;
    private static final RedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local available = redis.call('GET', KEYS[1])
            if not available then
                return 0
            end
            available = tonumber(available)
            local requested = tonumber(ARGV[1])
            if available < requested then
                return 0
            end
            redis.call('DECRBY', KEYS[1], requested)
            return 1
            """, Long.class);
    private static final RedisScript<Long> RESTORE_ONCE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return -1
            end
            local available = redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
            return available
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean reserve(String productId, int quantity) {
        validateProductId(productId);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Long reserved = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(stockKey(productId)),
                Integer.toString(quantity));
        return Long.valueOf(1L).equals(reserved);
    }

    public boolean restoreOnce(String orderId, String productId, int quantity) {
        validateProductId(productId);
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Long result = redisTemplate.execute(
                RESTORE_ONCE_SCRIPT,
                List.of(stockKey(productId), "stock-compensated:" + orderId),
                Integer.toString(quantity),
                Long.toString(COMPENSATION_MARKER_TTL_SECONDS));
        if (result == null) {
            throw new IllegalStateException("Redis did not return the compensation result");
        }
        return result != -1L;
    }

    public boolean initializeIfAbsent(String productId, int initialQuantity) {
        validateProductId(productId);
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("Initial quantity cannot be negative");
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                stockKey(productId), Integer.toString(initialQuantity)));
    }

    public int currentStock(String productId) {
        validateProductId(productId);
        String value = redisTemplate.opsForValue().get(stockKey(productId));
        return value == null ? 0 : Integer.parseInt(value);
    }

    private String stockKey(String productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID is required");
        }
    }
}