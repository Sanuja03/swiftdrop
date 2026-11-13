package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.zone.entity.Zone;

/**
 * @param bookedOn booking date in the business time zone, used for the delivery estimate
 */
public record PricingContext(PricingRule rule, Zone pickupZone, Zone dropoffZone, BigDecimal weightKg,
                             LocalDate bookedOn) { }
