package com.swiftdrop.backend.modules.webhook.endpoint;

import java.util.UUID;

/**
 * Where to send a merchant's webhooks and the shared secret used to sign them.
 */
public record WebhookEndpoint(UUID merchantId, String url, String secret) {

    /** Never print the secret, even in logs or exception messages. */
    @Override
    public String toString() {
        return "WebhookEndpoint[merchantId=" + merchantId + ", url=" + url + ", secret=***]";
    }
}
