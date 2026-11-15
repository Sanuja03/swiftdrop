package com.swiftdrop.backend.modules.messaging.entity;

/**
 * Whether an outbox row has reached RabbitMQ yet.
 */
public enum OutboxStatus {
    /** Saved with the business change, waiting for the relay (or for its next retry). */
    PENDING,
    /** Confirmed by RabbitMQ. */
    PUBLISHED
}
