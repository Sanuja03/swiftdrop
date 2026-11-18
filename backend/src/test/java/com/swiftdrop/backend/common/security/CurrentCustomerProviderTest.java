package com.swiftdrop.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;

class CurrentCustomerProviderTest {
    private static final String EMAIL = "customer@example.test";
    private static final UUID USER_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentCustomerProvider provider = new CurrentCustomerProvider(userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheUserFromTheJwt() {
        signInAs(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(USER_ID).email(EMAIL).fullName("Test Customer").build()));

        assertThat(provider.currentCustomer()).isEqualTo(new CurrentCustomer(USER_ID, "Test Customer"));
    }

    @Test
    void nobodySignedInIsRejected() {
        assertThatThrownBy(provider::currentCustomer).isInstanceOf(NotSignedInException.class);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void anonymousUserIsRejected() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(provider::currentCustomer).isInstanceOf(NotSignedInException.class);
    }

    @Test
    void deletedUserIsRejected() {
        signInAs(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(provider::currentCustomer).isInstanceOf(NotSignedInException.class);
    }

    private static void signInAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }
}
