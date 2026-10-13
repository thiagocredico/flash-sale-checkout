package br.com.ada.flashsale.checkout.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ada.flashsale.checkout.api.CreateOrderRequest;
import br.com.ada.flashsale.contracts.OrderStatus;
import br.com.ada.flashsale.contracts.OrderStatusUpdatedEvent;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs the real Flyway migration and JPA mapping against a genuine PostgreSQL
 * server (embedded, no Docker required) instead of mocks.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CheckoutPersistenceIT {

    private static EmbeddedPostgres embeddedPostgres;

    @Autowired
    private OrderProjectionRepository orderRepository;

    @Autowired
    private CheckoutOutboxEventRepository outboxRepository;

    @BeforeAll
    static void startDatabase() {
        try {
            embeddedPostgres = EmbeddedPostgres.start();
        } catch (IOException | RuntimeException exception) {
            Assumptions.abort("Embedded PostgreSQL indisponível neste ambiente: " + exception.getMessage());
        }
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Test
    void persistsOrderProjectionAndAppliesStatusEventAgainstRealPostgres() {
        var orderId = UUID.randomUUID();
        var now = LocalDateTime.now().withNano(0);
        var request = new CreateOrderRequest("PROD-001", 2, "50329291076", "Fernando Augusto");

        orderRepository.saveAndFlush(OrderProjection.processing(orderId, request, now));

        var persisted = orderRepository.findById(orderId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(persisted.getProdutoId()).isEqualTo("PROD-001");

        persisted.applyStatus(new OrderStatusUpdatedEvent(orderId.toString(), OrderStatus.CONFIRMED, now.plusMinutes(1)));
        orderRepository.saveAndFlush(persisted);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void persistsOutboxEventAndListsOnlyUnpublishedOnesAgainstRealPostgres() {
        var pendingEvent = new CheckoutOutboxEvent(UUID.randomUUID(), "order-created", "{}", LocalDateTime.now());
        var publishedEvent = new CheckoutOutboxEvent(UUID.randomUUID(), "order-created", "{}", LocalDateTime.now());
        publishedEvent.markPublished(LocalDateTime.now());

        outboxRepository.saveAndFlush(pendingEvent);
        outboxRepository.saveAndFlush(publishedEvent);

        var pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(pending).extracting(CheckoutOutboxEvent::getEventId).containsExactly(pendingEvent.getEventId());
    }
}
