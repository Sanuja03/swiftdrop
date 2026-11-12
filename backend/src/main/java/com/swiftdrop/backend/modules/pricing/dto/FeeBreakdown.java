package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;

public record FeeBreakdown(BigDecimal baseFee, BigDecimal weightCharge, BigDecimal zoneCharge,
                           BigDecimal totalFee, String currency) {
    public static final String CURRENCY = "LKR";
}
