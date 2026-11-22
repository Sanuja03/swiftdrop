package com.swiftdrop.backend.dev;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

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
 * Local development only ("dev" profile): adds approved demo merchants so the
 * merchant
 * portal and the merchant API can be tried end to end.
 */
@Slf4j
@Profile(DevProfile.NAME)
@Component
@ConditionalOnProperty(name = "app.dev.seed-merchant", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DemoMerchantSeeder implements ApplicationRunner {

    public static final String EMAIL = "test.merchant@example.com";
    public static final String BUSINESS_NAME = "Demo Merchant";
    public static final String WEBHOOK_URL = "https://example.com/webhook";

    public static final String SWIFTDROP_MERCHANT_EMAIL = "merchant@example.com";
    public static final String SWIFTDROP_MERCHANT_NAME = "SwiftDrop Official Merchant";

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantService merchantService;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEV_MERCHANT_PASSWORD:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAccount(EMAIL, BUSINESS_NAME);
        seedAccount(SWIFTDROP_MERCHANT_EMAIL, SWIFTDROP_MERCHANT_NAME);
    }

    private void seedAccount(String email, String businessName) {
        User user = userRepository.findByEmail(email).orElseGet(() -> createUser(email, businessName));
        if (merchantRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }
        String apiKey = merchantService.newApiKey();
        merchantRepository.save(Merchant.builder()
                .userId(user.getId())
                .businessName(businessName)
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .approvalNotes("Dev profile demo merchant")
                .apiKey(apiKey)
                .webhookUrl(WEBHOOK_URL)
                .webhookSecret(merchantService.newWebhookSecret())
                .build());
        log.info("Dev demo merchant '{}' created for {}. API key: {}", businessName, email, apiKey);
    }

    private User createUser(String email, String businessName) {
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? randomPassword() : configuredPassword;
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(businessName)
                .role(Role.ROLE_MERCHANT)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build());
        if (generated) {
            log.info("Dev demo merchant user {} created. Password (shown once; set DEV_MERCHANT_PASSWORD in .env to choose one): {}",
                    email, password);
        } else {
            log.info("Dev demo merchant user {} created with configured DEV_MERCHANT_PASSWORD", email);
        }
        return user;
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
