package com.swiftdrop.backend.modules.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Everything needed to book a delivery: the same fields as a fee estimate plus addresses
 * and recipient details.
 */
public record CreateParcelRequest(
        @NotNull UUID pickupZoneId,
        @NotNull UUID dropoffZoneId,
        @NotNull ParcelSize parcelSize,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal weightKg,
        @NotNull ServiceType serviceType,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String dropoffAddress,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Pattern(regexp = PHONE_PATTERN) String recipientPhone) {

    /** Digits with optional leading +, spaces or dashes; 7 to 20 characters. */
    public static final String PHONE_PATTERN = "^\\+?[0-9][0-9 -]{5,18}[0-9]$";

    @AssertTrue(message = "weightKg must be within the selected parcel size range")
    public boolean isWeightWithinParcelSize() {
        return toEstimateRequest().isWeightWithinParcelSize();
    }

    /** The pricing part of this booking, so the fee always comes from PricingService. */
    public EstimateRequest toEstimateRequest() {
        return new EstimateRequest(pickupZoneId, dropoffZoneId, parcelSize, weightKg, serviceType);
    }
}
