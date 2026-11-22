package com.swiftdrop.backend.modules.merchant.dto;

public class WebhookTestRequest {
    private String webhookUrl;

    public WebhookTestRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String webhookUrl;

        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }

        public WebhookTestRequest build() {
            WebhookTestRequest r = new WebhookTestRequest();
            r.webhookUrl = this.webhookUrl;
            return r;
        }
    }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
}
