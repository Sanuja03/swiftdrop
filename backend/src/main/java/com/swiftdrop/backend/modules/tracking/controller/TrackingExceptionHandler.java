package com.swiftdrop.backend.modules.tracking.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;

/**
 * Turns tracking and receipt errors into RFC 9457 problem responses.
 * Runs before any catch-all global handler so these keep their proper status codes.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
        "com.swiftdrop.backend.modules.tracking",
        "com.swiftdrop.backend.modules.receipt"
})
public class TrackingExceptionHandler {

    @ExceptionHandler(ParcelNotFoundException.class)
    public ProblemDetail handleNotFound(ParcelNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Parcel not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidStatusTransitionException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid status change", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate() {
        return problem(HttpStatus.CONFLICT, "Parcel was updated at the same time",
                "Someone else changed this parcel just now. Reload and try again.");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidInput() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Tracking numbers are 6 to 32 letters, digits or dashes.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
