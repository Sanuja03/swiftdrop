package com.swiftdrop.backend.modules.merchant.dto;

import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import java.time.Instant;
import java.util.UUID;

public class MerchantProfileResponse {
    private UUID id;
    private UUID userId;
    private String businessName;
    private String registrationNumber;
    private String contactPhone;
    private String businessAddress;
    private MerchantRegistrationStatus registrationStatus;
    private String apiKey;
    private String webhookUrl;
    private boolean webhookConfigured;
    private Instant createdAt;
    private Instant updatedAt;

    public MerchantProfileResponse() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID userId;
        private String businessName;
        private String registrationNumber;
        private String contactPhone;
        private String businessAddress;
        private MerchantRegistrationStatus registrationStatus;
        private String apiKey;
        private String webhookUrl;
        private boolean webhookConfigured;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder businessName(String businessName) { this.businessName = businessName; return this; }
        public Builder registrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; return this; }
        public Builder contactPhone(String contactPhone) { this.contactPhone = contactPhone; return this; }
        public Builder businessAddress(String businessAddress) { this.businessAddress = businessAddress; return this; }
        public Builder registrationStatus(MerchantRegistrationStatus registrationStatus) { this.registrationStatus = registrationStatus; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder webhookConfigured(boolean webhookConfigured) { this.webhookConfigured = webhookConfigured; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public MerchantProfileResponse build() {
            MerchantProfileResponse r = new MerchantProfileResponse();
            r.id = this.id;
            r.userId = this.userId;
            r.businessName = this.businessName;
            r.registrationNumber = this.registrationNumber;
            r.contactPhone = this.contactPhone;
            r.businessAddress = this.businessAddress;
            r.registrationStatus = this.registrationStatus;
            r.apiKey = this.apiKey;
            r.webhookUrl = this.webhookUrl;
            r.webhookConfigured = this.webhookConfigured;
            r.createdAt = this.createdAt;
            r.updatedAt = this.updatedAt;
            return r;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getBusinessAddress() { return businessAddress; }
    public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }

    public MerchantRegistrationStatus getRegistrationStatus() { return registrationStatus; }
    public void setRegistrationStatus(MerchantRegistrationStatus registrationStatus) { this.registrationStatus = registrationStatus; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public boolean isWebhookConfigured() { return webhookConfigured; }
    public void setWebhookConfigured(boolean webhookConfigured) { this.webhookConfigured = webhookConfigured; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
