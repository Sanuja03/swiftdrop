package com.swiftdrop.backend.modules.booking.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.backend.modules.booking.exception.BookingException;

/**
 * Turns booking errors into RFC 9457 problem responses, in the same shape as
 * TrackingExceptionHandler. Invalid request bodies and zone or pricing errors are
 * handled by PricingExceptionHandler.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(BookingException.class)
    public ProblemDetail handleBookingException(BookingException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getTitle());
        return problem;
    }
}
