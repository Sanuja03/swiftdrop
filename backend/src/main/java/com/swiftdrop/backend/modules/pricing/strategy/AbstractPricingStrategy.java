package com.swiftdrop.backend.modules.pricing.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;

abstract class AbstractPricingStrategy implements PricingStrategy {
    protected FeeBreakdown breakdown(PricingContext context) {
        var rule = context.rule();
        BigDecimal weightCharge = context.weightKg().multiply(rule.getPerKgFee());
        BigDecimal zoneCharge = context.pickupZone().getId().equals(context.dropoffZone().getId())
                ? context.dropoffZone().getSurcharge()
                : rule.getInterZoneFee().add(context.dropoffZone().getSurcharge());
        BigDecimal base = rule.getBaseFee().setScale(2, RoundingMode.HALF_UP);
        weightCharge = weightCharge.setScale(2, RoundingMode.HALF_UP);
        zoneCharge = zoneCharge.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(weightCharge).add(zoneCharge).setScale(2, RoundingMode.HALF_UP);
        return new FeeBreakdown(base, weightCharge, zoneCharge, total, FeeBreakdown.CURRENCY);
    }
}
