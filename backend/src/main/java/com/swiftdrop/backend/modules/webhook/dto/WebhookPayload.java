package com.swiftdrop.backend.modules.webhook.dto;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;

/**
 * The JSON body a merchant receives. Deliberately small: no internal ids, addresses or
 * phone numbers, only what the merchant needs to update their own order.
 *
 * <pre>
 * {
 *   "id": "4f0c...",                      // event id: ignore a repeat you already processed
 *   "type": "parcel.status_changed",
 *   "createdAt": "2026-11-13T09:30:00Z",
 *   "data": { "trackingNumber": "SDP7845621", "status": "IN_TRANSIT", "statusLabel": "In Transit",
 *             "previousStatus": "PICKED_UP", "note": null, "changedAt": "2026-11-13T09:30:00Z" }
 * }
 * </pre>
 */
public record WebhookPayload(UUID id, String type, Instant createdAt, Data data) {

    public record Data(String trackingNumber, String status, String statusLabel, String previousStatus,
                       String note, Instant changedAt) {
    }

    public static WebhookPayload parcelStatusChanged(UUID eventId, String eventType, Instant createdAt,
                                                     ParcelStatusChangedPayload event) {
        return new WebhookPayload(eventId, eventType, createdAt, new Data(
                event.trackingNumber(),
                event.status().name(),
                event.status().getLabel(),
                event.previousStatus() == null ? null : event.previousStatus().name(),
                event.note(),
                event.changedAt()));
    }
}
