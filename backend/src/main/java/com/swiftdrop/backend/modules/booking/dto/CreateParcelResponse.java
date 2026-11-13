package com.swiftdrop.backend.modules.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

public record CreateParcelResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        BigDecimal totalFee,
        String currency,
        Instant createdAt,
        Instant estimatedDeliveryAt) {

    public static CreateParcelResponse from(Parcel parcel, String currency) {
        return new CreateParcelResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getFee(), currency, parcel.getCreatedAt(), parcel.getEstimatedDeliveryAt());
    }
}
