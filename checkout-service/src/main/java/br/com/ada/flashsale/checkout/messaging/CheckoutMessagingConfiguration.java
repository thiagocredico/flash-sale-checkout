package br.com.ada.flashsale.checkout.messaging;

import br.com.ada.flashsale.contracts.OrderQueues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CheckoutMessagingConfiguration {

    @Bean
    Queue orderCreatedQueue() {
        return queueWithDeadLetter(OrderQueues.ORDER_CREATED);
    }

    @Bean
    Queue orderStatusUpdatedQueue() {
        return queueWithDeadLetter(OrderQueues.ORDER_STATUS_UPDATED);
    }

    @Bean
    Queue orderCompensationQueue() {
        return queueWithDeadLetter(OrderQueues.ORDER_COMPENSATION);
    }

    @Bean
    Queue orderCreatedDeadLetterQueue() {
        return new Queue(deadLetterQueueName(OrderQueues.ORDER_CREATED), true);
    }

    @Bean
    Queue orderStatusUpdatedDeadLetterQueue() {
        return new Queue(deadLetterQueueName(OrderQueues.ORDER_STATUS_UPDATED), true);
    }

    @Bean
    Queue orderCompensationDeadLetterQueue() {
        return new Queue(deadLetterQueueName(OrderQueues.ORDER_COMPENSATION), true);
    }

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        var rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(250, 2.0, 2000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    private Queue queueWithDeadLetter(String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange("")
                .deadLetterRoutingKey(deadLetterQueueName(queueName))
                .build();
    }

    private String deadLetterQueueName(String queueName) {
        return queueName + ".dlq";
    }
}