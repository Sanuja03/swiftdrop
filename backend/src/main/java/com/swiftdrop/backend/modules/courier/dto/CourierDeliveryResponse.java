package com.swiftdrop.backend.modules.courier.dto;

import com.swiftdrop.backend.modules.courier.entity.CourierAssignment;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CourierDeliveryResponse(UUID parcelId, String trackingNumber, LocalDate scheduledDate,
                                      ParcelStatus status, String senderName, String pickupAddress,
                                      String pickupCity, String recipientName, String recipientPhone,
                                      String dropoffAddress, String dropoffCity) {

    public static CourierDeliveryResponse from(CourierAssignment assignment) {
        var parcel = assignment.getParcel();
        return new CourierDeliveryResponse(parcel.getId(), parcel.getTrackingNumber(),
                assignment.getScheduledDate(), parcel.getStatus(), parcel.getSenderName(),
                parcel.getPickupAddress(), parcel.getPickupCity(), parcel.getRecipientName(),
                parcel.getRecipientPhone(), parcel.getDropoffAddress(), parcel.getDropoffCity());
    }
}
