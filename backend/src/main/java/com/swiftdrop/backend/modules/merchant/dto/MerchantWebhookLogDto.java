package com.swiftdrop.backend.modules.merchant.dto;

import com.swiftdrop.backend.modules.merchant.entity.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public class MerchantWebhookLogDto {
    private UUID id;
    private UUID merchantId;
    private String trackingNumber;
    private String eventType;
    private String payload;
    private String endpointUrl;
    private Integer responseCode;
    private WebhookDeliveryStatus deliveryStatus;
    private String errorMessage;
    private int attemptCount;
    private Instant createdAt;

    public MerchantWebhookLogDto() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID merchantId;
        private String trackingNumber;
        private String eventType;
        private String payload;
        private String endpointUrl;
        private Integer responseCode;
        private WebhookDeliveryStatus deliveryStatus;
        private String errorMessage;
        private int attemptCount;
        private Instant createdAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder merchantId(UUID merchantId) { this.merchantId = merchantId; return this; }
        public Builder trackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder payload(String payload) { this.payload = payload; return this; }
        public Builder endpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; return this; }
        public Builder responseCode(Integer responseCode) { this.responseCode = responseCode; return this; }
        public Builder deliveryStatus(WebhookDeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder attemptCount(int attemptCount) { this.attemptCount = attemptCount; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public MerchantWebhookLogDto build() {
            MerchantWebhookLogDto d = new MerchantWebhookLogDto();
            d.id = this.id;
            d.merchantId = this.merchantId;
            d.trackingNumber = this.trackingNumber;
            d.eventType = this.eventType;
            d.payload = this.payload;
            d.endpointUrl = this.endpointUrl;
            d.responseCode = this.responseCode;
            d.deliveryStatus = this.deliveryStatus;
            d.errorMessage = this.errorMessage;
            d.attemptCount = this.attemptCount;
            d.createdAt = this.createdAt;
            return d;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }

    public WebhookDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(WebhookDeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
