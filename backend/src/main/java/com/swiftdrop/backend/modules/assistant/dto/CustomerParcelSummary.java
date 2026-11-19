package com.swiftdrop.backend.modules.assistant.dto;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Safe, sanitized parcel summary for customer assistant queries.
 * Omits street addresses, phone numbers, and internal IDs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerParcelSummary {

    private String trackingNumber;
    private String status;
    private String statusLabel;
    private String pickupCity;
    private String dropoffCity;
    private String serviceType;
    private String serviceLabel;
    private BigDecimal fee;
    private Instant estimatedDeliveryAt;
    private Instant createdAt;

    public static CustomerParcelSummary fromEntity(Parcel parcel) {
        if (parcel == null) {
            return null;
        }
        return CustomerParcelSummary.builder()
                .trackingNumber(parcel.getTrackingNumber())
                .status(parcel.getStatus() != null ? parcel.getStatus().name() : null)
                .statusLabel(parcel.getStatus() != null ? parcel.getStatus().getLabel() : null)
                .pickupCity(parcel.getPickupCity())
                .dropoffCity(parcel.getDropoffCity())
                .serviceType(parcel.getServiceType() != null ? parcel.getServiceType().name() : null)
                .serviceLabel(parcel.getServiceType() != null ? parcel.getServiceType().getLabel() : null)
                .fee(parcel.getFee())
                .estimatedDeliveryAt(parcel.getEstimatedDeliveryAt())
                .createdAt(parcel.getCreatedAt())
                .build();
    }
}
