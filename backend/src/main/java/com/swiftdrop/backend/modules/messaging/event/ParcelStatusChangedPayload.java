package com.swiftdrop.backend.modules.messaging.event;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;

/**
 * Payload of a {@link ParcelEvents#STATUS_CHANGED} event. Holds no addresses or phone
 * numbers; consumers that need contact details load them by {@code parcelId}.
 *
 * @param previousStatus null when the parcel has just been created
 */
public record ParcelStatusChangedPayload(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus previousStatus,
        ParcelStatus status,
        String note,
        Instant changedAt) {

    public static ParcelStatusChangedPayload from(ParcelStatusChangedEvent event) {
        return new ParcelStatusChangedPayload(event.parcelId(), event.trackingNumber(),
                event.oldStatus(), event.newStatus(), event.note(), event.changedAt());
    }
}
