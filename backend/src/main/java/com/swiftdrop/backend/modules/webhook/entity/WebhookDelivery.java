package com.swiftdrop.backend.modules.webhook.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One event being delivered to one merchant, with the result of its latest attempt.
 * The body is fixed when the delivery is created, so every retry sends exactly the same
 * JSON (only the signature timestamp changes).
 */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDelivery {

    static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    /** The outbox event id; together with merchantId it is unique, which blocks duplicates. */
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "parcel_id", updatable = false)
    private UUID parcelId;

    @Column(name = "tracking_number", length = 32, updatable = false)
    private String trackingNumber;

    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;

    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_response_code")
    private Integer lastResponseCode;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: a retry and a manual retry cannot both record the same attempt. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static WebhookDelivery create(UUID eventId, UUID merchantId, UUID parcelId, String trackingNumber,
                                         String eventType, String endpointUrl, String payload, int maxAttempts) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.id = UUID.randomUUID();
        delivery.eventId = eventId;
        delivery.merchantId = merchantId;
        delivery.parcelId = parcelId;
        delivery.trackingNumber = trackingNumber;
        delivery.eventType = eventType;
        delivery.endpointUrl = endpointUrl;
        delivery.payload = payload;
        delivery.maxAttempts = maxAttempts;
        delivery.status = WebhookDeliveryStatus.PENDING;
        return delivery;
    }

    public void recordSuccess(String url, int responseCode, Instant at) {
        recordAttempt(url, responseCode, null, at);
        status = WebhookDeliveryStatus.DELIVERED;
        deliveredAt = at;
        nextAttemptAt = null;
    }

    public void recordFailure(String url, Integer responseCode, String error, Instant at) {
        recordAttempt(url, responseCode, error, at);
    }

    public boolean hasAttemptsLeft() {
        return attemptCount < maxAttempts;
    }

    public void scheduleRetry(Instant at) {
        status = WebhookDeliveryStatus.RETRYING;
        nextAttemptAt = at;
    }

    public void markDead() {
        status = WebhookDeliveryStatus.DEAD;
        nextAttemptAt = null;
    }

    public boolean isFinished() {
        return status == WebhookDeliveryStatus.DELIVERED || status == WebhookDeliveryStatus.DEAD;
    }

    public boolean canRetryManually() {
        return status == WebhookDeliveryStatus.DEAD;
    }

    /**
     * Gives a dead delivery exactly one more attempt; if that fails it is dead again.
     */
    public void reopenForManualRetry() {
        if (!canRetryManually()) {
            throw new IllegalStateException("Only a dead delivery can be retried manually");
        }
        status = WebhookDeliveryStatus.PENDING;
        maxAttempts = attemptCount + 1;
    }

    private void recordAttempt(String url, Integer responseCode, String error, Instant at) {
        attemptCount++;
        endpointUrl = url;
        lastResponseCode = responseCode;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        lastAttemptAt = at;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
