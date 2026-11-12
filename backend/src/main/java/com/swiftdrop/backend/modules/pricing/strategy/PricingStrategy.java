package com.swiftdrop.backend.modules.pricing.strategy;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;

public interface PricingStrategy {
    ServiceType supports();
    FeeBreakdown calculate(PricingContext context);
}
