package com.swiftdrop.backend.modules.webhook.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.messaging.config.MessagingTopology;

import lombok.RequiredArgsConstructor;

/**
 * Parks a delivery in the retry queue for {@code delay}. When the queue's TTL expires,
 * RabbitMQ moves the message to the attempts queue and the consumer tries again.
 * Nothing is held in memory, so pending retries survive an application restart.
 */
@Component
@RequiredArgsConstructor
public class WebhookRetryScheduler {

    private final RabbitTemplate rabbitTemplate;

    public void schedule(UUID deliveryId, Duration delay) {
        rabbitTemplate.convertAndSend(MessagingTopology.WEBHOOK_EXCHANGE,
                MessagingTopology.retryRoutingKey(delay), new WebhookRetryMessage(deliveryId));
    }

    /** Body of a message on the retry and attempts queues. */
    public record WebhookRetryMessage(UUID deliveryId) {
    }
}
