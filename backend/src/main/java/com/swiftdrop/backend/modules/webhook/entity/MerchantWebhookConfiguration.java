package com.swiftdrop.backend.modules.webhook.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 128)
    private String webhookSecret;

    public static MerchantWebhookConfiguration forUser(UUID userId) {
        MerchantWebhookConfiguration configuration = new MerchantWebhookConfiguration();
        configuration.userId = userId;
        return configuration;
    }

    public void configure(String url, String secret) {
        webhookUrl = url;
        webhookSecret = secret;
    }
}
