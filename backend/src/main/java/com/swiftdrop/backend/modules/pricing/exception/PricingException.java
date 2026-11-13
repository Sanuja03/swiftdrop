package com.swiftdrop.backend.modules.pricing.exception;

import org.springframework.http.HttpStatus;

public class PricingException extends RuntimeException {
    private final HttpStatus status;
    private final String title;

    public PricingException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }

    public static PricingException unknownZone(Object id) {
        return new PricingException(HttpStatus.NOT_FOUND, "Zone not found", "Active zone %s was not found.".formatted(id));
    }
    public static PricingException inactiveRule(Object type) {
        return new PricingException(HttpStatus.UNPROCESSABLE_ENTITY, "Pricing rule inactive", "No active pricing rule is available for %s.".formatted(type));
    }
    public static PricingException sameDayAcrossZones() {
        return new PricingException(HttpStatus.UNPROCESSABLE_ENTITY, "Same Day unavailable", "Same Day delivery is available only when pickup and drop-off are in the same zone.");
    }
}
