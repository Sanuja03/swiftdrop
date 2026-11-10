package com.swiftdrop.backend.modules.merchant.dto;

public class WebhookTestResponse {
    private boolean success;
    private int statusCode;
    private String message;
    private long latencyMs;
    private String endpointUrl;
    private String simulatedPayload;

    public WebhookTestResponse() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean success;
        private int statusCode;
        private String message;
        private long latencyMs;
        private String endpointUrl;
        private String simulatedPayload;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder statusCode(int statusCode) { this.statusCode = statusCode; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder latencyMs(long latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder endpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; return this; }
        public Builder simulatedPayload(String simulatedPayload) { this.simulatedPayload = simulatedPayload; return this; }

        public WebhookTestResponse build() {
            WebhookTestResponse r = new WebhookTestResponse();
            r.success = this.success;
            r.statusCode = this.statusCode;
            r.message = this.message;
            r.latencyMs = this.latencyMs;
            r.endpointUrl = this.endpointUrl;
            r.simulatedPayload = this.simulatedPayload;
            return r;
        }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getSimulatedPayload() { return simulatedPayload; }
    public void setSimulatedPayload(String simulatedPayload) { this.simulatedPayload = simulatedPayload; }
}
