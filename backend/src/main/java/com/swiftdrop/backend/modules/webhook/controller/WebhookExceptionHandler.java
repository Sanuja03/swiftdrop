package com.swiftdrop.backend.modules.webhook.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.swiftdrop.backend.modules.webhook.exception.WebhookException;

/**
 * Turns webhook API errors into RFC 9457 problem responses, in the same shape as
 * TrackingExceptionHandler and BookingExceptionHandler.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class WebhookExceptionHandler {

    @ExceptionHandler(WebhookException.class)
    public ProblemDetail handleWebhookException(WebhookException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getTitle());
        return problem;
    }
}
