package br.com.ada.flashsale.checkout.cache;

import br.com.ada.flashsale.checkout.api.OrderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisOrderCache {

    private static final String ORDER_CACHE_KEY_PREFIX = "order:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisOrderCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<OrderResponse> find(UUID orderId) {
        String key = cacheKey(orderId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached, OrderResponse.class));
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void put(OrderResponse order) {
        try {
            String json = objectMapper.writeValueAsString(order);
            redisTemplate.opsForValue().set(cacheKey(order.pedidoId()), json, CACHE_TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize order cache entry", exception);
        }
    }

    public void evict(UUID orderId) {
        redisTemplate.delete(cacheKey(orderId));
    }

    private String cacheKey(UUID orderId) {
        return ORDER_CACHE_KEY_PREFIX + orderId;
    }
}