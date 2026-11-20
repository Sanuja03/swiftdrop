package com.swiftdrop.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.merchant.service.MerchantService;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;

class DemoMerchantSeederTest {
    private static final UUID USER_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final DemoMerchantSeeder seeder = new DemoMerchantSeeder(userRepository, merchantRepository,
            new MerchantService(merchantRepository, mock(ParcelRepository.class), mock(com.swiftdrop.backend.modules.messaging.service.OutboxService.class)), passwordEncoder);

    @Test
    void createsAnActiveMerchantUserAndAnApprovedMerchantWithAGeneratedKey() {
        ReflectionTestUtils.setField(seeder, "configuredPassword", "");
        when(userRepository.findByEmail(DemoMerchantSeeder.EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(DemoMerchantSeeder.SWIFTDROP_MERCHANT_EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(USER_ID);
            return user;
        });
        when(merchantRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        seeder.run(null);

        var user = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(user.capture());
        assertThat(user.getValue().getRole()).isEqualTo(Role.ROLE_MERCHANT);
        assertThat(user.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getValue().getPasswordHash()).isEqualTo("hashed");

        var merchant = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository, org.mockito.Mockito.atLeastOnce()).save(merchant.capture());
        assertThat(merchant.getAllValues().get(0).getBusinessName()).isEqualTo("Demo Merchant");
        assertThat(merchant.getAllValues().get(0).getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.APPROVED);
        assertThat(merchant.getAllValues().get(0).getWebhookUrl()).isEqualTo("https://example.com/webhook");
        assertThat(merchant.getAllValues().get(0).getApiKey()).startsWith("sdp_live_").hasSizeGreaterThan(30);
    }

    @Test
    void doesNothingWhenTheDemoMerchantAlreadyExists() {
        var existing = User.builder().id(USER_ID).email(DemoMerchantSeeder.EMAIL).fullName("Demo Merchant").build();
        var existing2 = User.builder().id(UUID.fromString("70000000-0000-4000-8000-000000000002")).email(DemoMerchantSeeder.SWIFTDROP_MERCHANT_EMAIL).fullName(DemoMerchantSeeder.SWIFTDROP_MERCHANT_NAME).build();
        when(userRepository.findByEmail(DemoMerchantSeeder.EMAIL)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail(DemoMerchantSeeder.SWIFTDROP_MERCHANT_EMAIL)).thenReturn(Optional.of(existing2));
        when(merchantRepository.findByUserId(USER_ID)).thenReturn(Optional.of(Merchant.builder().userId(USER_ID).build()));
        when(merchantRepository.findByUserId(existing2.getId())).thenReturn(Optional.of(Merchant.builder().userId(existing2.getId()).build()));

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(merchantRepository, never()).save(any());
    }
}
