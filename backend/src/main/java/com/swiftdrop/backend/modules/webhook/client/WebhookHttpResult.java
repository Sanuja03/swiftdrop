package com.swiftdrop.backend.modules.webhook.client;

/**
 * Outcome of one HTTP call to a merchant endpoint.
 *
 * @param statusCode null when no response arrived (timeout, connection refused, bad URL)
 * @param error      short reason for a failure; null on success
 */
public record WebhookHttpResult(Integer statusCode, String error) {

    public static WebhookHttpResult response(int statusCode) {
        boolean ok = statusCode >= 200 && statusCode < 300;
        return new WebhookHttpResult(statusCode, ok ? null : "Endpoint answered HTTP " + statusCode);
    }

    public static WebhookHttpResult noResponse(String error) {
        return new WebhookHttpResult(null, error);
    }

    /** Only a 2xx answer counts; redirects are not followed and count as failures. */
    public boolean isSuccess() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
