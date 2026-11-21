package com.swiftdrop.backend.modules.webhook.entity;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Minimal merchant mapping shared with JdbcMerchantWebhookDirectory. Never returned by an API. */
@Entity
@Table(name = "merchants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantWebhookConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    // Required when lazily creating a profile for a legacy merchant user. These
    // fields are insert-only here: webhook updates must not change portal metadata.
    @Column(name = "business_name", nullable = false, updatable = false)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, updatable = false)
    // Only WebhookConfigurationService's verified ACTIVE-user fallback creates this
    // projection. Preserve the eligibility MerchantSecurityConfig grants those legacy
    // users when no profile exists; never change the status of an existing profile.
    private MerchantRegistrationStatus registrationStatus = MerchantRegistrationStatus.APPROVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    public static MerchantWebhookConfiguration forUser(UUID userId, String businessName) {
        MerchantWebhookConfiguration configuration = new MerchantWebhookConfiguration();
        configuration.userId = userId;
        configuration.businessName = businessName;
        return configuration;
    }

    public void configure(String url, String secret) {
        webhookUrl = url;
        webhookSecret = secret;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
