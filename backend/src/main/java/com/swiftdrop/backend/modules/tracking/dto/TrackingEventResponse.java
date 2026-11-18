package com.swiftdrop.backend.modules.tracking.dto;

import java.time.Instant;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.entity.ParcelStatusHistory;

/**
 * One step of the public timeline. Notes are left out on purpose, as they may hold personal details.
 */
public record TrackingEventResponse(ParcelStatus status, String label, Instant changedAt) {

    public static TrackingEventResponse from(ParcelStatusHistory entry) {
        return new TrackingEventResponse(entry.getToStatus(), entry.getToStatus().getLabel(), entry.getChangedAt());
    }
}
