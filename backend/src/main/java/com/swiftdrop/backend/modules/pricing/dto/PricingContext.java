package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;

import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.zone.entity.Zone;

public record PricingContext(PricingRule rule, Zone pickupZone, Zone dropoffZone, BigDecimal weightKg) { }
