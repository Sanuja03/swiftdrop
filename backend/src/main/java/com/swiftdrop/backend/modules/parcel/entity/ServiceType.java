package com.swiftdrop.backend.modules.parcel.entity;

/**
 * Delivery service levels a customer or merchant can book.
 */
public enum ServiceType {
    STANDARD("Standard (1-3 working days)"),
    EXPRESS("Express (next day)"),
    SAME_DAY("Same Day");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
