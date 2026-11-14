//Sends parcel status change events to RabbitMQ

package com.swiftdrop.backend.modules.messaging.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.backend.modules.messaging.config.MessagingProperties;
import com.swiftdrop.backend.modules.messaging.config.MessagingTopology;
import com.swiftdrop.backend.modules.messaging.entity.OutboxEvent;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Relays pending outbox rows to RabbitMQ. A row is marked published only after the
 * broker confirms it, so a crash in between causes a duplicate rather than a lost event;
 * consumers ignore duplicates by event id (at-least-once delivery).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.messaging.outbox", name = "publisher-enabled", matchIfMissing = true)
public class OutboxPublisher {

    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final MessagingProperties.Outbox settings;
    private final Clock clock;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                           TransactionTemplate transactionTemplate, JsonMapper jsonMapper,
                           MessagingProperties properties, Clock clock) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.jsonMapper = jsonMapper;
        this.settings = properties.outbox();
        this.clock = clock;
    }

    /**
     * Relays batches until no full batch is left, then waits for the next poll.
     */
    @Scheduled(fixedDelayString = "${app.messaging.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        Integer relayed;
        do {
            relayed = transactionTemplate.execute(status -> relayBatch());
        } while (relayed != null && relayed == settings.batchSize());
    }

    /**
     * Publishes one batch of due rows inside the caller's transaction.
     *
     * @return how many rows were picked up (published or not)
     */
    int relayBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(clock.instant(), settings.batchSize());
        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.markPublished(clock.instant());
            } catch (RuntimeException ex) {
                Duration backoff = backoffAfter(event.getPublishAttempts() + 1);
                event.markPublishFailed(describe(ex), clock.instant().plus(backoff));
                log.warn("Could not publish outbox event {} ({}), retrying in {}s: {}",
                        event.getId(), event.getEventType(), backoff.toSeconds(), describe(ex));
            }
        }
        return batch.size();
    }

    private void send(OutboxEvent event) {
        Message message = toMessage(event);
        rabbitTemplate.invoke(operations -> {
            operations.send(MessagingTopology.EVENTS_EXCHANGE, event.getRoutingKey(), message);
            if (rabbitTemplate.getConnectionFactory().isSimplePublisherConfirms()) {
                operations.waitForConfirmsOrDie(settings.confirmTimeout().toMillis());
            }
            return null;
        });
    }

    private Message toMessage(OutboxEvent event) {
        DomainEventMessage envelope = new DomainEventMessage(event.getId(), event.getEventType(),
                event.getAggregateType(), event.getAggregateId(), event.getCreatedAt(),
                jsonMapper.readTree(event.getPayload()));

        return MessageBuilder.withBody(jsonMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setTimestamp(Date.from(event.getCreatedAt()))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    /** 2s, 4s, 8s ... capped at the configured maximum. */
    Duration backoffAfter(int attempts) {
        int doublings = Math.min(Math.max(attempts - 1, 0), 20);
        Duration backoff = FIRST_BACKOFF.multipliedBy(1L << doublings);
        return backoff.compareTo(settings.maxBackoff()) > 0 ? settings.maxBackoff() : backoff;
    }

    private static String describe(RuntimeException ex) {
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }
}
