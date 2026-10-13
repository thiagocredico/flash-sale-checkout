package br.com.ada.flashsale.checkout.api;

import br.com.ada.flashsale.checkout.persistence.CheckoutOrderPersistence;
import br.com.ada.flashsale.checkout.stock.RedisStockService;
import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.contracts.OrderStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CreateOrderService {

    private final RedisStockService stockService;
    private final CheckoutOrderPersistence orderPersistence;
    private final Clock clock;

    public CreateOrderService(
            RedisStockService stockService,
            CheckoutOrderPersistence orderPersistence,
            Clock clock) {
        this.stockService = stockService;
        this.orderPersistence = orderPersistence;
        this.clock = clock;
    }

    public CreateOrderResponse create(CreateOrderRequest request) {
        if (!stockService.reserve(request.produtoId(), request.quantidade())) {
            throw new InsufficientStockException(request.produtoId());
        }

        UUID orderId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        var event = new OrderCreatedEvent(
                orderId.toString(),
                request.produtoId(),
                request.quantidade(),
                request.clienteCpf(),
                request.clienteNome(),
                now);

        try {
                orderPersistence.save(orderId, request, event, now);
        } catch (RuntimeException submissionFailure) {
            compensateLocalFailure(orderId, request, submissionFailure);
            throw new OrderSubmissionException(submissionFailure);
        }

        return new CreateOrderResponse(orderId, OrderStatus.PROCESSING, now);
    }

    private void compensateLocalFailure(
            UUID orderId,
            CreateOrderRequest request,
            RuntimeException submissionFailure) {
        try {
            stockService.restoreOnce(orderId.toString(), request.produtoId(), request.quantidade());
        } catch (RuntimeException restoreFailure) {
            submissionFailure.addSuppressed(restoreFailure);
        }
    }
}