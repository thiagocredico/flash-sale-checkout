package br.com.ada.flashsale.checkout.messaging;

import br.com.ada.flashsale.checkout.persistence.CheckoutOutboxEvent;
import br.com.ada.flashsale.checkout.persistence.CheckoutOutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CheckoutOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOutboxPublisher.class);

    private final CheckoutOutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public CheckoutOutboxPublisher(
            CheckoutOutboxEventRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${order.outbox.delay-ms:1000}")
    public void publishPending() {
        for (CheckoutOutboxEvent event : outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                publish(event);
                event.markPublished(LocalDateTime.now(clock));
                outboxRepository.save(event);
            } catch (RuntimeException exception) {
                log.warn("Could not publish outbox event {}, will retry on next tick", event.getEventId(), exception);
            }
        }
    }

    private void publish(CheckoutOutboxEvent event) {
        var correlation = new CorrelationData(event.getEventId().toString());
        var message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.getEventId().toString())
                .build();
        rabbitTemplate.send("", event.getRoutingKey(), message, correlation);
        try {
            var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ did not route event " + event.getEventId());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing event " + event.getEventId(), exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not confirm event " + event.getEventId(), exception);
        }
    }
}