package com.swiftdrop.backend.modules.webhook.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.webhook.entity.MerchantWebhookConfiguration;

public interface MerchantWebhookConfigurationRepository extends JpaRepository<MerchantWebhookConfiguration, UUID> {
    Optional<MerchantWebhookConfiguration> findByUserId(UUID userId);
}
