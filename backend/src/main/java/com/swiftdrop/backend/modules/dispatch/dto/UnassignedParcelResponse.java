package com.swiftdrop.backend.modules.dispatch.dto;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnassignedParcelResponse {

    private UUID id;
    private String trackingNumber;
    private String senderName;
    private String recipientName;
    private String recipientPhone;
    private String pickupAddress;
    private String pickupCity;
    private String dropoffAddress;
    private String dropoffCity;
    private ServiceType serviceType;
    private BigDecimal weightKg;
    private BigDecimal fee;
    private ParcelStatus status;
    private Instant estimatedDeliveryAt;
    private Instant createdAt;

    public static UnassignedParcelResponse fromEntity(Parcel parcel) {
        if (parcel == null) {
            return null;
        }
        return UnassignedParcelResponse.builder()
                .id(parcel.getId())
                .trackingNumber(parcel.getTrackingNumber())
                .senderName(parcel.getSenderName())
                .recipientName(parcel.getRecipientName())
                .recipientPhone(parcel.getRecipientPhone())
                .pickupAddress(parcel.getPickupAddress())
                .pickupCity(parcel.getPickupCity())
                .dropoffAddress(parcel.getDropoffAddress())
                .dropoffCity(parcel.getDropoffCity())
                .serviceType(parcel.getServiceType())
                .weightKg(parcel.getWeightKg())
                .fee(parcel.getFee())
                .status(parcel.getStatus())
                .estimatedDeliveryAt(parcel.getEstimatedDeliveryAt())
                .createdAt(parcel.getCreatedAt())
                .build();
    }
}
