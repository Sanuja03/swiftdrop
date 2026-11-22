package com.swiftdrop.backend.modules.tracking.dto;

import java.time.Instant;
import java.util.List;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.entity.ParcelStatusHistory;

/**
 * What anyone with a tracking number may see. Shows cities only, never names or street addresses.
 */
public record PublicTrackingResponse(
        String trackingNumber,
        ParcelStatus status,
        String statusLabel,
        String originCity,
        String destinationCity,
        ServiceType serviceType,
        String serviceLabel,
        Instant estimatedDeliveryAt,
        Instant lastUpdatedAt,
        List<TrackingEventResponse> timeline) {

    public static PublicTrackingResponse from(Parcel parcel, List<ParcelStatusHistory> history) {
        List<TrackingEventResponse> timeline = history.stream().map(TrackingEventResponse::from).toList();
        Instant lastUpdatedAt = timeline.isEmpty()
                ? parcel.getUpdatedAt()
                : timeline.getLast().changedAt();

        return new PublicTrackingResponse(
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                parcel.getStatus().getLabel(),
                parcel.getPickupCity(),
                parcel.getDropoffCity(),
                parcel.getServiceType(),
                parcel.getServiceType().getLabel(),
                parcel.getEstimatedDeliveryAt(),
                lastUpdatedAt,
                timeline);
    }
}
