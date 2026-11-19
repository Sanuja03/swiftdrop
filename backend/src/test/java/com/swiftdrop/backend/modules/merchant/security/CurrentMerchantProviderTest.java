package com.swiftdrop.backend.modules.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.swiftdrop.backend.common.security.NotSignedInException;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;

class CurrentMerchantProviderTest {
    private static final String EMAIL = "test.merchant@example.com";
    private static final UUID USER_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");

    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentMerchantProvider provider = new CurrentMerchantProvider(merchantRepository, userRepository);
    private final Merchant merchant = Merchant.builder().id(MERCHANT_ID).userId(USER_ID).businessName("Test Shop").build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apiKeyRequestUsesTheKeysMerchant() {
        authenticate(new MerchantPrincipal(MERCHANT_ID, USER_ID), MerchantApiKeyAuthFilter.API_KEY_AUTHORITY);
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        assertThat(provider.currentMerchant()).isSameAs(merchant);
    }

    @Test
    void jwtRequestUsesTheSignedInUsersMerchant() {
        authenticate(EMAIL, "ROLE_MERCHANT");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().id(USER_ID).email(EMAIL).fullName("Test").build()));
        when(merchantRepository.findByUserId(USER_ID)).thenReturn(Optional.of(merchant));

        assertThat(provider.currentMerchant()).isSameAs(merchant);
    }

    @Test
    void userWithoutMerchantProfileIsNotFound() {
        authenticate(EMAIL, "ROLE_MERCHANT");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(User.builder().id(USER_ID).email(EMAIL).fullName("Test").build()));
        when(merchantRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(provider::currentMerchant).isInstanceOf(MerchantNotFoundException.class);
    }

    @Test
    void nobodySignedInIsRejected() {
        assertThatThrownBy(provider::currentMerchant).isInstanceOf(NotSignedInException.class);
    }

    private static void authenticate(Object principal, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(authority))));
    }
}
