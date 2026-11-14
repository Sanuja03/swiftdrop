package com.swiftdrop.backend.modules.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

/**
 * A booking as its owner sees it, for the confirmation page. Only ever returned to the
 * customer who booked it, since it holds addresses and recipient details.
 */
public record BookingDetailsResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        String pickupAddress,
        UUID pickupZoneId,
        String pickupZoneName,
        String dropoffAddress,
        UUID dropoffZoneId,
        String dropoffZoneName,
        ParcelSize parcelSize,
        BigDecimal weightKg,
        ServiceType serviceType,
        String recipientName,
        String recipientPhone,
        BigDecimal baseFee,
        BigDecimal weightCharge,
        BigDecimal zoneCharge,
        BigDecimal totalFee,
        String currency,
        Instant createdAt,
        Instant estimatedDeliveryAt) {

    public static BookingDetailsResponse from(Parcel parcel, String currency) {
        return new BookingDetailsResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getPickupAddress(), parcel.getPickupZoneId(), parcel.getPickupCity(),
                parcel.getDropoffAddress(), parcel.getDropoffZoneId(), parcel.getDropoffCity(),
                parcel.getParcelSize(), parcel.getWeightKg(), parcel.getServiceType(),
                parcel.getRecipientName(), parcel.getRecipientPhone(),
                parcel.getBaseFee(), parcel.getWeightCharge(), parcel.getZoneCharge(), parcel.getFee(),
                currency, parcel.getCreatedAt(), parcel.getEstimatedDeliveryAt());
    }
}
