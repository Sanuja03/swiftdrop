package com.swiftdrop.backend.modules.webhook.entity;

/**
 * Where a webhook delivery is in its life.
 *
 * <pre>
 * PENDING -> DELIVERED
 *         -> RETRYING -> (attempt again) -> DELIVERED | RETRYING | DEAD
 * DEAD -> PENDING (only when someone retries it manually)
 * </pre>
 */
public enum WebhookDeliveryStatus {
    /** Created, first attempt not finished yet. */
    PENDING,
    /** Last attempt failed; another one is scheduled at next_attempt_at. */
    RETRYING,
    /** Merchant answered 2xx. */
    DELIVERED,
    /** Every attempt failed; the message went to the dead-letter queue. */
    DEAD
}
