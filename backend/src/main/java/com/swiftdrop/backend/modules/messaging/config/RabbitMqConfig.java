package com.swiftdrop.backend.modules.messaging.config;

import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.DEAD_LETTER_EXCHANGE;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.EVENTS_EXCHANGE;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_ATTEMPTS_QUEUE;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_ATTEMPT_KEY;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_DEAD_LETTER_KEY;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_DEAD_LETTER_QUEUE;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_EVENTS_BINDING;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_EVENTS_QUEUE;
import static com.swiftdrop.backend.modules.messaging.config.MessagingTopology.WEBHOOK_EXCHANGE;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the RabbitMQ exchanges and queues (see {@link MessagingTopology}). Spring AMQP
 * creates them on the broker the first time the application connects, so no manual setup
 * is needed. All queues and exchanges are durable and survive a broker restart.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMqConfig {

    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange webhookExchange() {
        return ExchangeBuilder.directExchange(WEBHOOK_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue webhookEventsQueue() {
        return deadLetteringQueue(WEBHOOK_EVENTS_QUEUE);
    }

    @Bean
    public Queue webhookAttemptsQueue() {
        return deadLetteringQueue(WEBHOOK_ATTEMPTS_QUEUE);
    }

    @Bean
    public Queue webhookDeadLetterQueue() {
        return QueueBuilder.durable(WEBHOOK_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding webhookEventsBinding() {
        return BindingBuilder.bind(webhookEventsQueue()).to(eventsExchange()).with(WEBHOOK_EVENTS_BINDING);
    }

    @Bean
    public Binding webhookAttemptsBinding() {
        return BindingBuilder.bind(webhookAttemptsQueue()).to(webhookExchange()).with(WEBHOOK_ATTEMPT_KEY);
    }

    @Bean
    public Binding webhookDeadLetterBinding() {
        return BindingBuilder.bind(webhookDeadLetterQueue()).to(deadLetterExchange()).with(WEBHOOK_DEAD_LETTER_KEY);
    }

    /**
     * One "waiting room" queue per retry delay. Nothing consumes them: each message waits
     * for the queue's TTL, then RabbitMQ dead-letters it back to the attempts queue.
     * Separate queues per delay avoid a long wait blocking a short one behind it.
     */
    @Bean
    public Declarables webhookRetryQueues(MessagingProperties properties) {
        List<Declarable> declarables = new ArrayList<>();
        properties.webhook().retryDelays().stream().distinct().forEach(delay -> {
            Queue queue = retryQueue(delay);
            declarables.add(queue);
            declarables.add(BindingBuilder.bind(queue).to(webhookExchange())
                    .with(MessagingTopology.retryRoutingKey(delay)));
        });
        return new Declarables(declarables);
    }

    /** Sends and reads message bodies as JSON using the application's own Jackson settings. */
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * Same as Spring Boot's default listener factory, except a message whose handler throws
     * goes to the dead-letter queue instead of being redelivered in a tight loop.
     */
    @Bean(MessagingTopology.WEBHOOK_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory webhookListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    private static Queue deadLetteringQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(WEBHOOK_DEAD_LETTER_KEY)
                .build();
    }

    private static Queue retryQueue(Duration delay) {
        return QueueBuilder.durable(MessagingTopology.retryQueue(delay))
                .ttl(Math.toIntExact(delay.toMillis()))
                .deadLetterExchange(WEBHOOK_EXCHANGE)
                .deadLetterRoutingKey(WEBHOOK_ATTEMPT_KEY)
                .build();
    }
}
