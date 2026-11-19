package com.swiftdrop.backend.modules.merchant.dto;

import jakarta.validation.constraints.NotBlank;

public class WebhookConfigRequest {

    @NotBlank(message = "Webhook URL is required")
    private String webhookUrl;

    private String webhookSecret;

    public WebhookConfigRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String webhookUrl;
        private String webhookSecret;

        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder webhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; return this; }

        public WebhookConfigRequest build() {
            WebhookConfigRequest r = new WebhookConfigRequest();
            r.webhookUrl = this.webhookUrl;
            r.webhookSecret = this.webhookSecret;
            return r;
        }
    }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
}
