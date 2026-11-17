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
import com.swiftdrop.backend.modules.tracking.exception.ParcelStatusRequirementException;

/**
 * Turns parcel and status-change errors into RFC 9457 problem responses, for every
 * controller (tracking, receipts, and the courier or dispatcher endpoints that call
 * updateStatus()). Runs before any catch-all handler so these keep their proper status codes.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class TrackingExceptionHandler {

    @ExceptionHandler(ParcelStatusRequirementException.class)
    public ProblemDetail missingRequirement(ParcelStatusRequirementException ex) {
        return problem(HttpStatus.CONFLICT, "Status requirements not met", ex.getMessage());
    }

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
                "One or more values in the request are not valid.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
