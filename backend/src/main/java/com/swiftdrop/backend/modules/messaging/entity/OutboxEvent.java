package com.swiftdrop.backend.modules.messaging.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An event saved in the same transaction as the change that caused it (transactional
 * outbox). If the transaction rolls back the event never existed; if it commits, the
 * relay keeps trying until RabbitMQ has it. The row id is also the event id that
 * consumers use to ignore duplicates.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 150, updatable = false)
    private String routingKey;

    /** Event body as JSON. */
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The relay ignores the row until this time; pushed back after each failed publish. */
    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static OutboxEvent pending(String aggregateType, UUID aggregateId, String eventType,
                                      String routingKey, String payload, Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.routingKey = routingKey;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.createdAt = now;
        event.availableAt = now;
        return event;
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    /**
     * Records a failed publish and hides the row from the relay until {@code retryAt}.
     */
    public void markPublishFailed(String error, Instant retryAt) {
        publishAttempts++;
        lastError = truncate(error);
        availableAt = retryAt;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ERROR_LENGTH);
    }
}
