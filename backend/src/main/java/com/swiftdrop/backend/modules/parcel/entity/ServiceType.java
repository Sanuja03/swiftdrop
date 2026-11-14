package com.swiftdrop.backend.modules.parcel.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Delivery service levels a customer can book.
 */
@Getter
@RequiredArgsConstructor
public enum ServiceType {
    STANDARD("Standard (1-3 working days)"),
    EXPRESS("Express (next day)"),
    SAME_DAY("Same Day");

    private final String label;
}
