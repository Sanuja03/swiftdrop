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
import com.swiftdrop.backend.modules.webhook.entity.MerchantWebhookConfiguration;
import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.repository.MerchantWebhookConfigurationRepository;
import com.swiftdrop.backend.modules.webhook.validation.WebhookUrlValidator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WebhookConfigurationService {
    private final UserRepository users;
    private final MerchantWebhookConfigurationRepository configurations;
    private final WebhookUrlValidator validator;
    private final EntityManager entityManager;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public WebhookConfigurationResponse get(String email) {
        User user = requireMerchant(email);
        return configurations.findByUserId(user.getId())
                .map(configuration -> response(configuration, null))
                .orElseGet(() -> new WebhookConfigurationResponse(null, false, null));
    }

    @Transactional
    public WebhookConfigurationResponse put(String email, String url) {
        User user = requireMerchant(email);
        String validatedUrl = validator.validate(url);
        // Serialize first-time creation and updates for this identity, including across replicas.
        // Locking the existing user also works when no merchant row has been created yet.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireActiveMerchant(user);
        MerchantWebhookConfiguration configuration = configurations.findByUserId(user.getId())
                .orElseGet(() -> MerchantWebhookConfiguration.forUser(user.getId()));
        String secret = configuration.getWebhookSecret();
        String generatedSecret = null;
        if (secret == null || secret.isBlank()) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            generatedSecret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            secret = generatedSecret;
        }
        configuration.configure(validatedUrl, secret);
        configurations.save(configuration);
        return response(configuration, generatedSecret);
    }

    private User requireMerchant(String email) {
        User user = users.findByEmail(email).orElseThrow(WebhookException::merchantAccountMissing);
        requireActiveMerchant(user);
        return user;
    }

    private static void requireActiveMerchant(User user) {
        if (user.getRole() != Role.ROLE_MERCHANT || user.getStatus() != AccountStatus.ACTIVE) {
            throw WebhookException.merchantAccountMissing();
        }
    }

    private static WebhookConfigurationResponse response(MerchantWebhookConfiguration configuration, String newSecret) {
        boolean configured = configuration.getWebhookUrl() != null && !configuration.getWebhookUrl().isBlank()
                && configuration.getWebhookSecret() != null && !configuration.getWebhookSecret().isBlank();
        return new WebhookConfigurationResponse(configuration.getWebhookUrl(), configured, newSecret);
    }
}
