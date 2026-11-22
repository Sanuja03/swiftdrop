package com.swiftdrop.backend.modules.merchant.exception;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

public class OrderCannotBeCancelledException extends RuntimeException {
    public OrderCannotBeCancelledException(String trackingNumber, ParcelStatus currentStatus) {
        super("Order %s with status %s cannot be cancelled. Cancellation is blocked to protect physical inventory: only orders in CREATED status (prior to courier pickup) can be cancelled."
                .formatted(trackingNumber, currentStatus.name()));
    }
}

