package br.com.ada.flashsale.processor.messaging;

import br.com.ada.flashsale.processor.persistence.OutboxEvent;
import br.com.ada.flashsale.processor.persistence.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public OutboxPublisher(
            OutboxEventRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${order.outbox.delay-ms:1000}")
    public void publishPending() {
        for (OutboxEvent event : outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                publish(event);
                event.markPublished(LocalDateTime.now(clock));
                outboxRepository.save(event);
            } catch (RuntimeException exception) {
                log.warn("Could not publish outbox event {}, will retry on next tick", event.getEventId(), exception);
            }
        }
    }

    private void publish(OutboxEvent event) {
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