package com.swiftdrop.backend.modules.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "business_address", columnDefinition = "TEXT")
    private String businessAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false)
    private MerchantRegistrationStatus registrationStatus = MerchantRegistrationStatus.PENDING;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "api_key", unique = true, length = 128)
    private String apiKey;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 128)
    private String webhookSecret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Merchant() {
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
        private MerchantRegistrationStatus registrationStatus = MerchantRegistrationStatus.PENDING;
        private UUID approvedBy;
        private String approvalNotes;
        private String apiKey;
        private String webhookUrl;
        private String webhookSecret;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder businessName(String businessName) { this.businessName = businessName; return this; }
        public Builder registrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; return this; }
        public Builder contactPhone(String contactPhone) { this.contactPhone = contactPhone; return this; }
        public Builder businessAddress(String businessAddress) { this.businessAddress = businessAddress; return this; }
        public Builder registrationStatus(MerchantRegistrationStatus registrationStatus) { this.registrationStatus = registrationStatus; return this; }
        public Builder approvedBy(UUID approvedBy) { this.approvedBy = approvedBy; return this; }
        public Builder approvalNotes(String approvalNotes) { this.approvalNotes = approvalNotes; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
        public Builder webhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Merchant build() {
            Merchant m = new Merchant();
            m.id = this.id;
            m.userId = this.userId;
            m.businessName = this.businessName;
            m.registrationNumber = this.registrationNumber;
            m.contactPhone = this.contactPhone;
            m.businessAddress = this.businessAddress;
            if (this.registrationStatus != null) m.registrationStatus = this.registrationStatus;
            m.approvedBy = this.approvedBy;
            m.approvalNotes = this.approvalNotes;
            m.apiKey = this.apiKey;
            m.webhookUrl = this.webhookUrl;
            m.webhookSecret = this.webhookSecret;
            m.createdAt = this.createdAt;
            m.updatedAt = this.updatedAt;
            return m;
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

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public String getApprovalNotes() { return approvalNotes; }
    public void setApprovalNotes(String approvalNotes) { this.approvalNotes = approvalNotes; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isApproved() {
        return registrationStatus == MerchantRegistrationStatus.APPROVED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
