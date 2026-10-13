package br.com.ada.flashsale.checkout.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import redis.embedded.RedisServer;

/**
 * Proves against a real (embedded, non-mocked) Redis server that concurrent
 * reservations never push the stock counter below zero.
 */
class RedisStockServiceConcurrencyIT {

    private static final String PRODUCT_ID = "PROD-001";
    private static final int INITIAL_STOCK = 100;
    private static final int QUANTITY_PER_ORDER = 3;
    private static final int CONCURRENT_ORDERS = 60;

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisStockService stockService;

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

        stockService = new RedisStockService(redisTemplate);
    }

    @AfterAll
    static void stopRealRedis() throws IOException {
        connectionFactory.destroy();
        redisServer.stop();
    }

    @Test
    void neverOversellsStockUnderConcurrentReservations() throws InterruptedException {
        stockService.initializeIfAbsent(PRODUCT_ID, INITIAL_STOCK);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ORDERS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_ORDERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_ORDERS);
        AtomicInteger successCount = new AtomicInteger();

        IntStream.range(0, CONCURRENT_ORDERS).forEach(i -> executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                if (stockService.reserve(PRODUCT_ID, QUANTITY_PER_ORDER)) {
                    successCount.incrementAndGet();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        }));

        readyLatch.await();
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        int maxPossibleSuccesses = INITIAL_STOCK / QUANTITY_PER_ORDER;
        assertThat(successCount.get()).isLessThanOrEqualTo(maxPossibleSuccesses);

        int remainingStock = readRawStock();
        assertThat(remainingStock).isGreaterThanOrEqualTo(0);
        assertThat(remainingStock).isEqualTo(INITIAL_STOCK - successCount.get() * QUANTITY_PER_ORDER);
    }

    @Test
    void restoreOnceReturnsReservedStockAfterCompensation() {
        String productId = "PROD-RESTORE-1";
        stockService.initializeIfAbsent(productId, INITIAL_STOCK);
        stockService.reserve(productId, QUANTITY_PER_ORDER);

        boolean restored = stockService.restoreOnce("order-restore-1", productId, QUANTITY_PER_ORDER);

        assertThat(restored).isTrue();
        assertThat(readRawStock(productId)).isEqualTo(INITIAL_STOCK);
    }

    @Test
    void restoreOnceIsIdempotentForSameOrderAgainstRealRedis() {
        String productId = "PROD-RESTORE-2";
        stockService.initializeIfAbsent(productId, INITIAL_STOCK);
        stockService.reserve(productId, QUANTITY_PER_ORDER);

        boolean firstRestore = stockService.restoreOnce("order-restore-2", productId, QUANTITY_PER_ORDER);
        boolean secondRestore = stockService.restoreOnce("order-restore-2", productId, QUANTITY_PER_ORDER);

        assertThat(firstRestore).isTrue();
        assertThat(secondRestore).isFalse();
        assertThat(readRawStock(productId)).isEqualTo(INITIAL_STOCK);
    }

    private static int readRawStock() {
        return readRawStock(PRODUCT_ID);
    }

    private static int readRawStock(String productId) {
        String value = redisTemplate.opsForValue().get("stock:" + productId);
        return Integer.parseInt(value);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
