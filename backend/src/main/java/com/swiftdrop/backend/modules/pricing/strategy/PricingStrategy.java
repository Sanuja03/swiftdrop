package com.swiftdrop.backend.modules.pricing.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;

/**
 * Prices one service type. PricingService picks the strategy by {@link #supports()},
 * so adding a service type means adding a strategy, not editing a switch.
 */
public interface PricingStrategy {
    /** The base fee already covers this much weight; only the weight above it is charged per kg. */
    BigDecimal INCLUDED_WEIGHT_KG = BigDecimal.ONE;

    ServiceType supports();

    /** Fee breakdown plus the estimated delivery time for this service. */
    FeeBreakdown calculate(PricingContext context);

    /** Date this service promises delivery for a parcel booked on {@code bookedOn} (business time zone). */
    LocalDate estimateDeliveryDate(LocalDate bookedOn);
}
