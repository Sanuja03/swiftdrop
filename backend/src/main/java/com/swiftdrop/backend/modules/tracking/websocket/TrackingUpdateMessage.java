package com.swiftdrop.backend.modules.tracking.websocket;

import java.time.Instant;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;

/**
 * Payload pushed to the public tracking page. Holds no personal data
 * (names, addresses or notes), because anyone with the tracking number can subscribe.
 */
public record TrackingUpdateMessage(
        String trackingNumber,
        ParcelStatus status,
        String statusLabel,
        Instant changedAt) {

    public static TrackingUpdateMessage from(ParcelStatusChangedEvent event) {
        return new TrackingUpdateMessage(
                event.trackingNumber(),
                event.newStatus(),
                event.newStatus().getLabel(),
                event.changedAt());
    }
}
