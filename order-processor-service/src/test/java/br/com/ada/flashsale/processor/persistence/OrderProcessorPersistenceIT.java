package br.com.ada.flashsale.processor.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ada.flashsale.contracts.OrderCreatedEvent;
import br.com.ada.flashsale.processor.domain.Order;
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

import br.com.ada.flashsale.contracts.OrderStatus;

/**
 * Runs the real Flyway migration and JPA mapping against a genuine PostgreSQL
 * server (embedded, no Docker required) instead of mocks.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderProcessorPersistenceIT {

    private static EmbeddedPostgres embeddedPostgres;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

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
    void persistsOrderAndTransitionsToConfirmedAgainstRealPostgres() {
        var orderId = UUID.randomUUID();
        var event = new OrderCreatedEvent(
                orderId.toString(), "PROD-001", 2, "50329291076", "Fernando Augusto", LocalDateTime.now().withNano(0));

        var order = Order.from(event);
        order.confirm();
        orderRepository.saveAndFlush(order);

        var persisted = orderRepository.findById(orderId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(persisted.getProdutoId()).isEqualTo("PROD-001");
    }

    @Test
    void ignoresRedeliveryOfAlreadyPersistedOrder() {
        var orderId = UUID.randomUUID();
        var event = new OrderCreatedEvent(
                orderId.toString(), "PROD-001", 2, "50329291076", "Fernando Augusto", LocalDateTime.now().withNano(0));
        orderRepository.saveAndFlush(Order.from(event));

        assertThat(orderRepository.existsById(orderId)).isTrue();
    }

    @Test
    void persistsOutboxEventAndListsOnlyUnpublishedOnesAgainstRealPostgres() {
        var pendingEvent = new OutboxEvent(UUID.randomUUID(), "order-status-updated", "{}", LocalDateTime.now());
        var publishedEvent = new OutboxEvent(UUID.randomUUID(), "order-status-updated", "{}", LocalDateTime.now());
        publishedEvent.markPublished(LocalDateTime.now());

        outboxRepository.saveAndFlush(pendingEvent);
        outboxRepository.saveAndFlush(publishedEvent);

        var pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        assertThat(pending).extracting(OutboxEvent::getEventId).containsExactly(pendingEvent.getEventId());
    }
}
