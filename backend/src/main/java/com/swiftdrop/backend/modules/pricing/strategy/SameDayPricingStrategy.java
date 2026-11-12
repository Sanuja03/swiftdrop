package com.swiftdrop.backend.modules.pricing.strategy;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;

@Component
public class SameDayPricingStrategy extends AbstractPricingStrategy {
    @Override public ServiceType supports() { return ServiceType.SAME_DAY; }
    @Override public FeeBreakdown calculate(PricingContext context) {
        if (!context.pickupZone().getId().equals(context.dropoffZone().getId())) {
            throw PricingException.sameDayAcrossZones();
        }
        return breakdown(context);
    }
}
