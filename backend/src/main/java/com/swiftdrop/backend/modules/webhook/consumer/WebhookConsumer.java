//Listens to the swiftdrop.webhooks.events queue and receives the events.


package com.swiftdrop.backend.modules.webhook.consumer;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.messaging.config.MessagingTopology;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.webhook.service.AttemptOutcome;
import com.swiftdrop.backend.modules.webhook.service.WebhookDispatchService;
import com.swiftdrop.backend.modules.webhook.service.WebhookRetryScheduler.WebhookRetryMessage;

import lombok.RequiredArgsConstructor;

/**
 * Receives events and due retries from RabbitMQ. A message is acknowledged once it has
 * been handled. When a delivery runs out of attempts, or a message cannot be processed at
 * all, it is rejected and RabbitMQ moves it to {@code swiftdrop.webhooks.dlq}.
 */
@Component
@RequiredArgsConstructor
public class WebhookConsumer {

    private final WebhookDispatchService dispatchService;

    @RabbitListener(queues = MessagingTopology.WEBHOOK_EVENTS_QUEUE,
            containerFactory = MessagingTopology.WEBHOOK_LISTENER_FACTORY)
    public void onEvent(DomainEventMessage event) {
        deadLetterIfExhausted(dispatchService.handleEvent(event), "event " + event.eventId());
    }

    @RabbitListener(queues = MessagingTopology.WEBHOOK_ATTEMPTS_QUEUE,
            containerFactory = MessagingTopology.WEBHOOK_LISTENER_FACTORY)
    public void onRetryDue(WebhookRetryMessage message) {
        deadLetterIfExhausted(dispatchService.retry(message.deliveryId()), "delivery " + message.deliveryId());
    }

    private static void deadLetterIfExhausted(AttemptOutcome outcome, String what) {
        if (outcome == AttemptOutcome.DEAD) {
            throw new AmqpRejectAndDontRequeueException("Webhook for " + what + " ran out of attempts");
        }
    }
}
