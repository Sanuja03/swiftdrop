package com.swiftdrop.backend.modules.pricing.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.backend.modules.pricing.exception.PricingException;

/**
 * RFC 9457 problems for the Booking & Pricing controllers (booking, pricing, zone packages).
 * Scoped to those packages so it never replaces another module's error format, such as the
 * auth module's validation errors.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
        "com.swiftdrop.backend.modules.booking",
        "com.swiftdrop.backend.modules.pricing",
        "com.swiftdrop.backend.modules.zone"
})
public class PricingExceptionHandler {
    @ExceptionHandler(PricingException.class)
    public ProblemDetail handlePricingException(PricingException ex) {
        return problem(ex.getStatus(), ex.getTitle(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ProblemDetail handleInvalidRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "One or more values in the request are not valid.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
