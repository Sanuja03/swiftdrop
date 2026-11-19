package com.swiftdrop.backend.modules.merchant.exception;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

public class OrderCannotBeCancelledException extends RuntimeException {
    public OrderCannotBeCancelledException(String trackingNumber, ParcelStatus currentStatus) {
        super("Order %s with status %s cannot be cancelled. Only orders in CREATED status can be cancelled."
                .formatted(trackingNumber, currentStatus.name()));
    }
}

