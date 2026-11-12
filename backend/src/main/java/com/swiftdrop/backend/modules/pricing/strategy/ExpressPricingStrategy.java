package com.swiftdrop.backend.modules.pricing.strategy;

import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;

@Component
public class ExpressPricingStrategy extends AbstractPricingStrategy {
    @Override public ServiceType supports() { return ServiceType.EXPRESS; }
    @Override public FeeBreakdown calculate(PricingContext context) { return breakdown(context); }
}
