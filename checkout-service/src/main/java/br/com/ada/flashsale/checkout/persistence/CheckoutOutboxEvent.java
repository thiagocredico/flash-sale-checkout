package br.com.ada.flashsale.checkout.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkout_outbox_event")
public class CheckoutOutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "routing_key", nullable = false, updatable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected CheckoutOutboxEvent() {
    }

    public CheckoutOutboxEvent(UUID eventId, String routingKey, String payload, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}