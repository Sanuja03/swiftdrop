package com.swiftdrop.backend.modules.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.validation.WebhookUrlValidator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import tools.jackson.databind.json.JsonMapper;

/** Small, context-free regression tests; the existing MockMvc suite covers HTTP/security/JPA. */
@ExtendWith(MockitoExtension.class)
class WebhookConfigurationServiceTest {
    private static final String EMAIL = "merchant@example.test";
    private static final String URL = "https://hooks.example.test/events";
    private static final String OLD_SECRET = "whsec_unit_test_only";

    @Mock UserRepository users;
    @Mock MerchantRepository merchants;
    @Mock WebhookUrlValidator validator;
    @Mock EntityManager entityManager;
    @InjectMocks WebhookConfigurationService service;

    private User user;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).email(EMAIL).role(Role.ROLE_MERCHANT)
                .status(AccountStatus.ACTIVE).emailVerified(true).build();
        merchant = Merchant.builder().id(UUID.randomUUID()).userId(user.getId())
                .businessName("Test shop").registrationNumber("TEST-ONLY")
                .registrationStatus(MerchantRegistrationStatus.APPROVED).build();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void firstConfigurationReturns32ByteSecretOnceAndGetOmitsIt() {
        when(merchants.findForWebhookUpdate(user.getId())).thenReturn(Optional.of(merchant));
        when(merchants.findByUserId(user.getId())).thenReturn(Optional.of(merchant));
        when(validator.validate(URL)).thenReturn(URL);

        var created = service.put(EMAIL, URL);
        assertThat(created.configured()).isTrue();
        assertThat(created.secret()).startsWith("whsec_");
        assertThat(Base64.getUrlDecoder().decode(created.secret().substring(6))).hasSize(32);
        assertThat(merchant.getWebhookSecret()).isEqualTo(created.secret());
        assertThat(service.get(EMAIL).secret()).isNull();
        assertThat(JsonMapper.builder().build().writeValueAsString(service.get(EMAIL)))
                .doesNotContain("secret", created.secret());
        assertThat(created.toString()).doesNotContain(created.secret(), URL);
    }

    @Test
    void routineSavePreservesExistingSecretAndApprovalProfile() {
        merchant.setWebhookSecret(OLD_SECRET);
        when(merchants.findForWebhookUpdate(user.getId())).thenReturn(Optional.of(merchant));
        when(validator.validate(URL)).thenReturn(URL);

        var saved = service.put(EMAIL, URL);
        assertThat(saved.secret()).isNull();
        assertThat(merchant.getWebhookSecret()).isEqualTo(OLD_SECRET);
        assertThat(merchant.getBusinessName()).isEqualTo("Test shop");
        assertThat(merchant.getRegistrationNumber()).isEqualTo("TEST-ONLY");
        assertThat(merchant.getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.APPROVED);
    }

    @Test
    void explicitRotationReplacesSecretAndRevalidatesStoredUrl() {
        merchant.setWebhookUrl(URL);
        merchant.setWebhookSecret(OLD_SECRET);
        when(merchants.findForWebhookUpdate(user.getId())).thenReturn(Optional.of(merchant));
        when(validator.validate(URL)).thenReturn(URL);

        var rotated = service.rotate(EMAIL);
        assertThat(rotated.secret()).startsWith("whsec_").isNotEqualTo(OLD_SECRET);
        assertThat(merchant.getWebhookSecret()).isEqualTo(rotated.secret());
        verify(validator).validate(URL);
    }

    @Test
    void missingProfileCannotCreateOrApproveMerchant() {
        when(validator.validate(URL)).thenReturn(URL);
        when(merchants.findForWebhookUpdate(user.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.put(EMAIL, URL)).isInstanceOf(WebhookException.class);
        verify(merchants, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void nonActiveUsersCannotReadOrMutateConfiguration(AccountStatus status) {
        user.setStatus(status);
        assertThatThrownBy(() -> service.get(EMAIL)).isInstanceOf(WebhookException.class);
        assertThatThrownBy(() -> service.put(EMAIL, URL)).isInstanceOf(WebhookException.class);
        assertThatThrownBy(() -> service.rotate(EMAIL)).isInstanceOf(WebhookException.class);
        verify(merchants, never()).findForWebhookUpdate(any());
    }

    @ParameterizedTest
    @EnumSource(value = MerchantRegistrationStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void unapprovedProfilesCannotReadOrOverwriteSecrets(MerchantRegistrationStatus status) {
        merchant.setRegistrationStatus(status);
        merchant.setWebhookSecret(OLD_SECRET);
        when(merchants.findByUserId(user.getId())).thenReturn(Optional.of(merchant));
        when(merchants.findForWebhookUpdate(user.getId())).thenReturn(Optional.of(merchant));
        when(validator.validate(URL)).thenReturn(URL);
        assertThatThrownBy(() -> service.get(EMAIL)).isInstanceOf(WebhookException.class);
        assertThatThrownBy(() -> service.put(EMAIL, URL)).isInstanceOf(WebhookException.class);
        assertThatThrownBy(() -> service.rotate(EMAIL)).isInstanceOf(WebhookException.class);
        assertThat(merchant.getWebhookSecret()).isEqualTo(OLD_SECRET);
    }

    @Test
    void unverifiedOrWrongRoleCannotUseConfiguration() {
        user.setEmailVerified(false);
        assertThatThrownBy(() -> service.get(EMAIL)).isInstanceOf(WebhookException.class);
        user.setEmailVerified(true);
        user.setRole(Role.ROLE_CUSTOMER);
        assertThatThrownBy(() -> service.put(EMAIL, URL)).isInstanceOf(WebhookException.class);
        verify(merchants, never()).findForWebhookUpdate(any());
    }

    @Test
    void eligibilityIsCheckedAgainAfterAcquiringUserLock() {
        when(validator.validate(URL)).thenReturn(URL);
        doAnswer(invocation -> { user.setStatus(AccountStatus.SUSPENDED); return null; })
                .when(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        assertThatThrownBy(() -> service.put(EMAIL, URL)).isInstanceOf(WebhookException.class);
        verify(merchants, never()).findForWebhookUpdate(any());
    }

    @Test
    void unsafeDestinationDoesNotMutateOrRotateExistingSecret() {
        merchant.setWebhookUrl(URL);
        merchant.setWebhookSecret(OLD_SECRET);
        when(validator.validate(URL)).thenThrow(WebhookException.invalidDestination());
        assertThatThrownBy(() -> service.put(EMAIL, URL)).isInstanceOf(WebhookException.class);
        verify(merchants, never()).findForWebhookUpdate(any());
        assertThat(merchant.getWebhookSecret()).isEqualTo(OLD_SECRET);
    }
}
