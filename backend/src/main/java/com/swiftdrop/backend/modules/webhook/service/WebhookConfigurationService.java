package com.swiftdrop.backend.modules.webhook.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.webhook.dto.WebhookConfigurationResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.validation.WebhookUrlValidator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WebhookConfigurationService {
    private final UserRepository users;
    private final MerchantRepository configurations;
    private final WebhookUrlValidator validator;
    private final EntityManager entityManager;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public WebhookConfigurationResponse get(String email) {
        User user = requireMerchant(email);
        Merchant configuration = configurations.findByUserId(user.getId()).orElseThrow(WebhookException::merchantAccountMissing);
        requireApproved(configuration);
        return response(configuration, null);
    }

    @Transactional
    public WebhookConfigurationResponse put(String email, String url) {
        User user = requireMerchant(email);
        String validatedUrl = validator.validate(url);
        // Serialize configuration and eligibility changes for this identity across replicas.
        // Profile creation/approval remains exclusively in the merchant onboarding workflow.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireActiveMerchant(user);
        Merchant configuration = configurations.findForWebhookUpdate(user.getId()).orElseThrow(WebhookException::merchantAccountMissing);
        // A waiting writer must refresh any already-managed instance before reading its secret.
        entityManager.refresh(configuration, LockModeType.PESSIMISTIC_WRITE);
        requireApproved(configuration);
        String secret = configuration.getWebhookSecret();
        String generatedSecret = null;
        if (secret == null || secret.isBlank()) {
            generatedSecret = newSecret();
            secret = generatedSecret;
        }
        configuration.setWebhookUrl(validatedUrl);
        configuration.setWebhookSecret(secret);
        return response(configuration, generatedSecret);
    }

    /** Existing secrets are preserved by PUT; replacement always requires this explicit action. */
    @Transactional
    public WebhookConfigurationResponse rotate(String email) {
        User user = requireMerchant(email);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireActiveMerchant(user);
        Merchant configuration = configurations.findForWebhookUpdate(user.getId()).orElseThrow(WebhookException::merchantAccountMissing);
        entityManager.refresh(configuration, LockModeType.PESSIMISTIC_WRITE);
        requireApproved(configuration);
        if (configuration.getWebhookUrl() == null || configuration.getWebhookUrl().isBlank()) {
            throw WebhookException.configurationRequired();
        }
        validator.validate(configuration.getWebhookUrl());
        String generatedSecret = newSecret();
        configuration.setWebhookSecret(generatedSecret);
        return response(configuration, generatedSecret);
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireApproved(Merchant merchant) {
        if (!merchant.isApproved()) {
            throw WebhookException.merchantAccountMissing();
        }
    }

    private User requireMerchant(String email) {
        User user = users.findByEmail(email).orElseThrow(WebhookException::merchantAccountMissing);
        requireActiveMerchant(user);
        return user;
    }

    private static void requireActiveMerchant(User user) {
        if (user.getRole() != Role.ROLE_MERCHANT || user.getStatus() != AccountStatus.ACTIVE || !user.isEmailVerified()) {
            throw WebhookException.merchantAccountMissing();
        }
    }

    private static WebhookConfigurationResponse response(Merchant configuration, String newSecret) {
        boolean configured = configuration.getWebhookUrl() != null && !configuration.getWebhookUrl().isBlank()
                && configuration.getWebhookSecret() != null && !configuration.getWebhookSecret().isBlank();
        return new WebhookConfigurationResponse(configuration.getWebhookUrl(), configured, newSecret);
    }
}
