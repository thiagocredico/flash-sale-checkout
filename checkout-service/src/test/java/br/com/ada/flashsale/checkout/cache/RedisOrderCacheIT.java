package br.com.ada.flashsale.checkout.cache;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ada.flashsale.checkout.api.OrderResponse;
import br.com.ada.flashsale.contracts.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import redis.embedded.RedisServer;

/**
 * Validates Cache-Aside TTL and eviction behaviour against a real (embedded) Redis server.
 */
class RedisOrderCacheIT {

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisOrderCache orderCache;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startRealRedis() throws IOException {
        int port = findFreePort();
        redisServer = RedisServer.newRedisServer()
                .port(port)
                .setting("bind 127.0.0.1")
                .build();
        redisServer.start();

        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        orderCache = new RedisOrderCache(redisTemplate, objectMapper);
    }

    @AfterAll
    static void stopRealRedis() throws IOException {
        connectionFactory.destroy();
        redisServer.stop();
    }

    @Test
    void putAppliesFiveMinuteTtlAndFindReturnsSameOrder() {
        var orderId = UUID.randomUUID();
        var order = new OrderResponse(
                orderId, "PROD-001", 2, "50329291076", "Fernando Augusto",
                OrderStatus.CONFIRMED, LocalDateTime.now().withNano(0));

        orderCache.put(order);

        Long ttlSeconds = redisTemplate.getExpire("order:" + orderId, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isGreaterThan(0).isLessThanOrEqualTo(Duration.ofMinutes(5).toSeconds());
        assertThat(orderCache.find(orderId)).contains(order);
    }

    @Test
    void evictRemovesCachedOrder() {
        var orderId = UUID.randomUUID();
        var order = new OrderResponse(
                orderId, "PROD-001", 2, "50329291076", "Fernando Augusto",
                OrderStatus.CONFIRMED, LocalDateTime.now().withNano(0));
        orderCache.put(order);

        orderCache.evict(orderId);

        assertThat(orderCache.find(orderId)).isEmpty();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
