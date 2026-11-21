package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;

public record AdminPricingRuleResponse(ServiceType serviceType, BigDecimal baseFee, BigDecimal perKgFee,
                                       BigDecimal interZoneFee, boolean active, String currency,
                                       Instant updatedAt) {
    public static AdminPricingRuleResponse from(PricingRule rule) {
        return new AdminPricingRuleResponse(rule.getServiceType(), rule.getBaseFee(), rule.getPerKgFee(),
                rule.getInterZoneFee(), rule.isActive(), FeeBreakdown.CURRENCY, rule.getUpdatedAt());
    }
}
