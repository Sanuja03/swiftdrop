package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FeeBreakdown(BigDecimal baseFee, BigDecimal weightCharge, BigDecimal zoneCharge,
                           BigDecimal totalFee, String currency, Instant estimatedDeliveryAt) {
    public static final String CURRENCY = "LKR";
}
