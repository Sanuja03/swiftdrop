package com.swiftdrop.backend.modules.tracking.dto;

import java.time.Instant;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

/**
 * One row of the signed-in customer's shipment list. Same fields as the public tracking
 * summary (without the timeline), so the dashboard and shipment tables can show either.
 */
public record CustomerShipmentResponse(
        String trackingNumber,
        ParcelStatus status,
        String statusLabel,
        String originCity,
        String destinationCity,
        ServiceType serviceType,
        String serviceLabel,
        Instant estimatedDeliveryAt,
        Instant createdAt,
        Instant lastUpdatedAt) {

    public static CustomerShipmentResponse from(Parcel parcel) {
        return new CustomerShipmentResponse(
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                parcel.getStatus().getLabel(),
                parcel.getPickupCity(),
                parcel.getDropoffCity(),
                parcel.getServiceType(),
                parcel.getServiceType().getLabel(),
                parcel.getEstimatedDeliveryAt(),
                parcel.getCreatedAt(),
                parcel.getUpdatedAt());
    }
}
