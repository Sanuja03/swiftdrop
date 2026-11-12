package com.swiftdrop.backend.modules.pricing.strategy;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;

@Component
public class StandardPricingStrategy extends AbstractPricingStrategy {
    static final int DELIVERY_WORKING_DAYS = 3;

    @Override public ServiceType supports() { return ServiceType.STANDARD; }
    @Override public FeeBreakdown calculate(PricingContext context) { return breakdown(context); }
    @Override public LocalDate estimateDeliveryDate(LocalDate bookedOn) {
        return DeliveryCalendar.addWorkingDays(bookedOn, DELIVERY_WORKING_DAYS);
    }
}
