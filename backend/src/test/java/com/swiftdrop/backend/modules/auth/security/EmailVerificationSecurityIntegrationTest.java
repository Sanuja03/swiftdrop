package com.swiftdrop.backend.modules.auth.security;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.EmailVerificationToken;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.EmailVerificationTokenRepository;
import com.swiftdrop.backend.modules.auth.repository.RefreshTokenRepository;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public HTTP route, actual security chain, service and H2 persistence; no external services. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EmailVerificationTokenRepository codes;
    @Autowired RefreshTokenRepository refreshTokens;

    @ParameterizedTest
    @EnumSource(Role.class)
    void arbitraryCodeCannotAuthenticateAnyAlreadyVerifiedActiveRole(Role role) throws Exception {
        User user = account(role, true);
        long sessions = refreshTokens.count();
        verify(user, "000000").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid Token"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        assertThat(refreshTokens.count()).isEqualTo(sessions);
    }

    @Test
    void firstVerificationAuthenticatesOnceAndReusedCodeCreatesNoFurtherSession() throws Exception {
        User user = account(Role.ROLE_CUSTOMER, false);
        EmailVerificationToken code = code(user, LocalDateTime.now().plusHours(1));
        verify(user, code.getToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        assertThat(codes.findById(code.getId()).orElseThrow().isVerified()).isTrue();
        long sessions = refreshTokens.count();
        verify(user, code.getToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        assertThat(refreshTokens.count()).isEqualTo(sessions);
    }

    @Test
    void expiredCodeDoesNotVerifyTheAccountOrCreateASession() throws Exception {
        User user = account(Role.ROLE_CUSTOMER, false);
        EmailVerificationToken code = code(user, LocalDateTime.now().minusMinutes(1));
        long sessions = refreshTokens.count();
        verify(user, code.getToken()).andExpect(status().isUnauthorized());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
        assertThat(codes.findById(code.getId()).orElseThrow().isVerified()).isFalse();
        assertThat(refreshTokens.count()).isEqualTo(sessions);
    }

    @Test
    void incorrectCodeDoesNotConsumeAValidCodeOrCreateASession() throws Exception {
        User user = account(Role.ROLE_CUSTOMER, false);
        EmailVerificationToken code = code(user, LocalDateTime.now().plusHours(1));
        long sessions = refreshTokens.count();
        verify(user, "000000").andExpect(status().isUnauthorized());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
        assertThat(codes.findById(code.getId()).orElseThrow().isVerified()).isFalse();
        assertThat(refreshTokens.count()).isEqualTo(sessions);
    }

    private User account(Role role, boolean verified) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@verification.test")
                .fullName("Verification regression").role(role).status(AccountStatus.ACTIVE)
                .emailVerified(verified).build());
    }

    private EmailVerificationToken code(User user, LocalDateTime expiry) {
        return codes.saveAndFlush(EmailVerificationToken.builder().user(user).token("456789")
                .expiryDate(expiry).verified(false).build());
    }

    private org.springframework.test.web.servlet.ResultActions verify(User user, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"code\":\"" + code + "\"}"));
    }
}
