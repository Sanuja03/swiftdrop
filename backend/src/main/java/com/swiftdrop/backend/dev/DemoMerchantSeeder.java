package com.swiftdrop.backend.dev;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.AuthProvider;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.merchant.service.MerchantService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Local development only ("dev" profile): adds one approved demo merchant so the merchant
 * portal and the merchant API can be tried end to end. Generic data only.
 *
 * <ul>
 *   <li>User {@value #EMAIL}, ROLE_MERCHANT. Password: the DEV_MERCHANT_PASSWORD environment
 *       variable, or, when it is not set, a random password logged once at startup.</li>
 *   <li>Merchant "Demo Merchant", APPROVED, webhook https://example.com/webhook.</li>
 *   <li>An API key generated at startup and logged once. Nothing secret is in the code.</li>
 * </ul>
 * Idempotent: if the user or the merchant already exists, it is left untouched (so the key is
 * only shown the first time; regenerate it in the merchant portal if you lose it).
 * Set app.dev.seed-merchant=false when running against a shared database.
 */
@Slf4j
@Profile(DevProfile.NAME)
@Component
@ConditionalOnProperty(name = "app.dev.seed-merchant", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DemoMerchantSeeder implements ApplicationRunner {

    static final String EMAIL = "test.merchant@example.com";
    static final String BUSINESS_NAME = "Demo Merchant";
    static final String WEBHOOK_URL = "https://example.com/webhook";

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantService merchantService;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEV_MERCHANT_PASSWORD:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User user = userRepository.findByEmail(EMAIL).orElseGet(this::createUser);
        if (merchantRepository.findByUserId(user.getId()).isPresent()) {
            log.info("Demo merchant {} already exists; skipping", EMAIL);
            return;
        }
        String apiKey = merchantService.newApiKey();
        merchantRepository.save(Merchant.builder()
                .userId(user.getId())
                .businessName(BUSINESS_NAME)
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .approvalNotes("Dev profile demo merchant")
                .apiKey(apiKey)
                .webhookUrl(WEBHOOK_URL)
                .webhookSecret(merchantService.newWebhookSecret())
                .build());
        log.info("Dev demo merchant '{}' created for {}. API key (shown once): {}", BUSINESS_NAME, EMAIL, apiKey);
    }

    private User createUser() {
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? randomPassword() : configuredPassword;
        User user = userRepository.save(User.builder()
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(BUSINESS_NAME)
                .role(Role.ROLE_MERCHANT)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build());
        if (generated) {
            log.info("Dev demo merchant user {} created. Password (shown once; set DEV_MERCHANT_PASSWORD to choose one): {}",
                    EMAIL, password);
        } else {
            log.info("Dev demo merchant user {} created with the password from DEV_MERCHANT_PASSWORD", EMAIL);
        }
        return user;
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
