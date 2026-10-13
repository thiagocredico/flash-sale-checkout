package br.com.ada.flashsale.checkout.api;

import br.com.ada.flashsale.checkout.cache.RedisOrderCache;
import br.com.ada.flashsale.checkout.persistence.OrderProjectionRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryService {

    private static final int MAX_ATTEMPTS = 3;

    private final RedisOrderCache orderCache;
    private final OrderProjectionRepository orderRepository;
    private final long retryDelayMillis;

    public OrderQueryService(
            RedisOrderCache orderCache,
            OrderProjectionRepository orderRepository,
            @Value("${order.query.retry-delay-ms:100}") long retryDelayMillis) {
        this.orderCache = orderCache;
        this.orderRepository = orderRepository;
        this.retryDelayMillis = retryDelayMillis;
    }

    public OrderResponse find(UUID orderId) {
        try {
            var cachedOrder = orderCache.find(orderId);
            if (cachedOrder.isPresent()) {
                return cachedOrder.get();
            }
        } catch (DataAccessException ignored) {
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var projection = orderRepository.findById(orderId);
            if (projection.isPresent()) {
                var response = OrderResponse.from(projection.get());
                try {
                    orderCache.put(response);
                } catch (DataAccessException ignored) {
                }
                return response;
            }
            if (attempt < MAX_ATTEMPTS) {
                pauseBeforeRetry();
            }
        }
        throw new OrderNotFoundException(orderId);
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying order lookup", exception);
        }
    }
}