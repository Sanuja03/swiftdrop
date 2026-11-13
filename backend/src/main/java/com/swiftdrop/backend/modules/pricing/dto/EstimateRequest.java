package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

public record EstimateRequest(
        @NotNull UUID pickupZoneId,
        @NotNull UUID dropoffZoneId,
        @NotNull ParcelSize parcelSize,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal weightKg,
        @NotNull ServiceType serviceType) {

    @AssertTrue(message = "weightKg must be within the selected parcel size range")
    public boolean isWeightWithinParcelSize() {
        if (parcelSize == null || weightKg == null) return true;
        return weightKg.compareTo(parcelSize.minWeight()) >= 0
                && weightKg.compareTo(parcelSize.maxWeight()) <= 0;
    }
}
