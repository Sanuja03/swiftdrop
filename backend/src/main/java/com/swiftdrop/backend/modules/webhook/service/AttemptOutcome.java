package com.swiftdrop.backend.modules.webhook.service;

/**
 * What happened when the engine handled an event or a retry.
 */
public enum AttemptOutcome {
    /** Nothing to send: no merchant, no endpoint, unknown event, or already handled. */
    SKIPPED,
    DELIVERED,
    RETRY_SCHEDULED,
    /** Out of attempts; the consumer sends the message to the dead-letter queue. */
    DEAD
}
