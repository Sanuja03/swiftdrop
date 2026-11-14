package com.swiftdrop.backend.modules.booking.exception;

import org.springframework.http.HttpStatus;

/**
 * A booking that cannot be completed; mapped to an RFC 9457 problem by BookingExceptionHandler.
 */
public class BookingException extends RuntimeException {
    private final HttpStatus status;
    private final String title;

    public BookingException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }

    public static BookingException trackingNumberUnavailable() {
        return new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "Tracking number unavailable",
                "A tracking number could not be assigned. Please try again.");
    }
}
