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
        return new PricingException(HttpStatus.UNPROCESSABLE_CONTENT, "Pricing rule inactive", "No active pricing rule is available for %s.".formatted(type));
    }
    public static PricingException zoneNotFound(Object id) {
        return new PricingException(HttpStatus.NOT_FOUND, "Zone not found", "Zone %s was not found.".formatted(id));
    }
    public static PricingException duplicateZoneCode(String code) {
        return new PricingException(HttpStatus.CONFLICT, "Zone code already used", "A zone with code %s already exists.".formatted(code));
    }
    public static PricingException ruleNotFound(Object type) {
        return new PricingException(HttpStatus.NOT_FOUND, "Pricing rule not found", "No pricing rule exists for %s.".formatted(type));
    }
    public static PricingException lastActiveZone() {
        return new PricingException(HttpStatus.CONFLICT, "At least one zone must stay active", "Activate another zone before deactivating this one.");
    }
    public static PricingException lastActiveService() {
        return new PricingException(HttpStatus.CONFLICT, "At least one service must stay active", "Activate another service before deactivating this one.");
    }
    public static PricingException sameDayAcrossZones() {
        return new PricingException(HttpStatus.UNPROCESSABLE_CONTENT, "Same Day unavailable", "Same Day delivery is available only when pickup and drop-off are in the same zone.");
    }
}
