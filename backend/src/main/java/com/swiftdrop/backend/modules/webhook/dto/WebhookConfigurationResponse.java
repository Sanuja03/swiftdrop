package com.swiftdrop.backend.modules.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** secret is present only in the PUT response that generated it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookConfigurationResponse(String url, boolean configured, String secret) {
    @Override
    public String toString() {
        return "WebhookConfigurationResponse[configured=" + configured + ", secret=***]";
    }
}
