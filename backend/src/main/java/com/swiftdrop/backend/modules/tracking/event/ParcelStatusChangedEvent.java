package com.swiftdrop.backend.modules.tracking.event;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

/**
 * Announced every time a parcel's status changes (including when it is created).
 *
 * <p>Published inside the status-update transaction, so listeners can choose:
 * <ul>
 *   <li>{@code @EventListener} or {@code @TransactionalEventListener(phase = BEFORE_COMMIT)}
 *       to write in the same transaction (for example an outbox row)</li>
 *   <li>{@code @TransactionalEventListener} (AFTER_COMMIT, the default) to react only
 *       once the change is saved (for example pushing to the browser)</li>
 * </ul>
 *
 * @param oldStatus null when the parcel has just been created
 */
public record ParcelStatusChangedEvent(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus oldStatus,
        ParcelStatus newStatus,
        String note,
        UUID changedBy,
        Instant changedAt) {
}
