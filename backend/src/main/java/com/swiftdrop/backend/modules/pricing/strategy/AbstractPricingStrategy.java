package com.swiftdrop.backend.modules.pricing.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;

abstract class AbstractPricingStrategy implements PricingStrategy {
    /** The base fee already covers this much weight; only the weight above it is charged per kg. */
    static final BigDecimal INCLUDED_WEIGHT_KG = BigDecimal.ONE;

    protected FeeBreakdown breakdown(PricingContext context) {
        var rule = context.rule();
        BigDecimal chargeableWeight = context.weightKg().subtract(INCLUDED_WEIGHT_KG).max(BigDecimal.ZERO);
        BigDecimal weightCharge = chargeableWeight.multiply(rule.getPerKgFee());
        BigDecimal zoneCharge = context.pickupZone().getId().equals(context.dropoffZone().getId())
                ? context.dropoffZone().getSurcharge()
                : rule.getInterZoneFee().add(context.dropoffZone().getSurcharge());
        BigDecimal base = rule.getBaseFee().setScale(2, RoundingMode.HALF_UP);
        weightCharge = weightCharge.setScale(2, RoundingMode.HALF_UP);
        zoneCharge = zoneCharge.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(weightCharge).add(zoneCharge).setScale(2, RoundingMode.HALF_UP);
        var estimatedDeliveryAt = DeliveryCalendar.endOfDeliveryDay(estimateDeliveryDate(context.bookedOn()));
        return new FeeBreakdown(base, weightCharge, zoneCharge, total, FeeBreakdown.CURRENCY, estimatedDeliveryAt);
    }
}
