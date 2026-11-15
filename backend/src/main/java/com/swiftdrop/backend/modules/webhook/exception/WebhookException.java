package com.swiftdrop.backend.modules.webhook.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * An error from the delivery-attempts API, carrying the HTTP status and title to send back.
 */
@Getter
public class WebhookException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    private WebhookException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }

    /** Also used when the delivery belongs to another merchant, so its existence is not revealed. */
    public static WebhookException deliveryNotFound(UUID id) {
        return new WebhookException(HttpStatus.NOT_FOUND, "Webhook delivery not found",
                "No webhook delivery with id " + id + ".");
    }

    public static WebhookException merchantAccountMissing() {
        return new WebhookException(HttpStatus.FORBIDDEN, "No merchant account",
                "Your user is not linked to a merchant account.");
    }

    public static WebhookException notRetryable() {
        return new WebhookException(HttpStatus.CONFLICT, "Delivery cannot be retried",
                "Only deliveries that have failed every attempt can be retried manually.");
    }
}
