package br.com.ada.flashsale.checkout.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ada.flashsale.checkout.persistence.CheckoutOutboxEvent;
import br.com.ada.flashsale.checkout.persistence.CheckoutOutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class CheckoutOutboxPublisherTest {

    @Mock
    private CheckoutOutboxEventRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void marksEventPublishedAfterBrokerAck() {
        var event = pendingEvent();
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
            return null;
        }).when(rabbitTemplate).send(eq(""), eq("order-created"), any(Message.class), any(CorrelationData.class));
        var publisher = new CheckoutOutboxPublisher(outboxRepository, rabbitTemplate, fixedClock());

        publisher.publishPending();

        assertThat(event.getPublishedAt()).isEqualTo(LocalDateTime.parse("2026-09-29T12:00:00"));
        verify(outboxRepository).save(event);
    }

    @Test
    void keepsEventPendingWhenBrokerNacksMessage() {
        var event = pendingEvent();
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(rabbitTemplate).send(eq(""), eq("order-created"), any(Message.class), any(CorrelationData.class));
        var publisher = new CheckoutOutboxPublisher(outboxRepository, rabbitTemplate, fixedClock());

        publisher.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        verify(outboxRepository, never()).save(event);
    }

    @Test
    void aFailingEventDoesNotBlockOrRollBackOtherEventsInTheSameBatch() {
        var failingEvent = pendingEvent();
        var healthyEvent = pendingEvent();
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(failingEvent, healthyEvent));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            boolean isFailingEvent = correlation.getId().equals(failingEvent.getEventId().toString());
            correlation.getFuture().complete(new CorrelationData.Confirm(!isFailingEvent, isFailingEvent ? "rejected" : ""));
            return null;
        }).when(rabbitTemplate).send(eq(""), eq("order-created"), any(Message.class), any(CorrelationData.class));

        var publisher = new CheckoutOutboxPublisher(outboxRepository, rabbitTemplate, fixedClock());

        publisher.publishPending();

        assertThat(failingEvent.getPublishedAt()).isNull();
        assertThat(healthyEvent.getPublishedAt()).isEqualTo(LocalDateTime.parse("2026-09-29T12:00:00"));
        verify(outboxRepository, never()).save(failingEvent);
        verify(outboxRepository).save(healthyEvent);
    }

    private CheckoutOutboxEvent pendingEvent() {
        return new CheckoutOutboxEvent(
                UUID.randomUUID(), "order-created", "{}", LocalDateTime.parse("2026-09-29T11:59:00"));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);
    }
}