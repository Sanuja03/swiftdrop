package com.swiftdrop.backend.modules.webhook.dto;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;

/**
 * One row of the webhook delivery log. Never includes the signing secret.
 */
public record WebhookDeliveryResponse(
        UUID id,
        UUID eventId,
        UUID merchantId,
        String trackingNumber,
        String eventType,
        String endpointUrl,
        WebhookDeliveryStatus status,
        int attemptCount,
        int maxAttempts,
        Integer lastResponseCode,
        String lastError,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        Instant deliveredAt,
        Instant createdAt,
        String payload) {

    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getMerchantId(),
                delivery.getTrackingNumber(),
                delivery.getEventType(),
                delivery.getEndpointUrl(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getMaxAttempts(),
                delivery.getLastResponseCode(),
                delivery.getLastError(),
                delivery.getLastAttemptAt(),
                delivery.getNextAttemptAt(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt(),
                delivery.getPayload());
    }
}
