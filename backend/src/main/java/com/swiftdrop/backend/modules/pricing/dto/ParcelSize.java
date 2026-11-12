package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;

public enum ParcelSize {
    DOCUMENTS("0.5"), SMALL("2"), MEDIUM("10"), LARGE("30");

    private final BigDecimal maxWeight;
    ParcelSize(String maxWeight) { this.maxWeight = new BigDecimal(maxWeight); }
    public BigDecimal maxWeight() { return maxWeight; }
    public BigDecimal minWeight() {
        return switch (this) {
            case DOCUMENTS, SMALL -> BigDecimal.ZERO;
            case MEDIUM -> new BigDecimal("2");
            case LARGE -> new BigDecimal("10");
        };
    }
}
