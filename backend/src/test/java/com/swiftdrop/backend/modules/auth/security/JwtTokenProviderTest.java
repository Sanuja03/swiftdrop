package com.swiftdrop.backend.modules.auth.security;

import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtTokenProvider.class);

    @Test
    @DisplayName("Application context fails to start when app.jwt.secret is not configured")
    void failsWithoutJwtSecret() {
        contextRunner.run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    @DisplayName("Application context fails to start when app.jwt.secret is blank")
    void failsWhenJwtSecretIsBlank() {
        contextRunner
                .withPropertyValues("app.jwt.secret=   ")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    @DisplayName("Constructor rejects null or blank secret")
    void constructorRejectsNullOrBlank() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(null, 3600000L));
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("", 3600000L));
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("   ", 3600000L));
    }

    @Test
    @DisplayName("Generates and validates token when valid JWT secret is provided")
    void succeedsWithConfiguredJwtSecret() {
        String testSecret = "SwiftDropTestSecretKeyMustBeAtLeast256BitsLongForHs256Signing1234";
        contextRunner
                .withPropertyValues("app.jwt.secret=" + testSecret)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    JwtTokenProvider provider = context.getBean(JwtTokenProvider.class);
                    User user = User.builder()
                            .id(UUID.randomUUID())
                            .email("user@swiftdrop.test")
                            .fullName("Test User")
                            .role(Role.ROLE_CUSTOMER)
                            .build();

                    String token = provider.generateAccessToken(user);
                    assertTrue(provider.validateToken(token));
                    assertEquals("user@swiftdrop.test", provider.getEmailFromToken(token));
                    assertEquals("ROLE_CUSTOMER", provider.getRoleFromToken(token));
                });
    }
}
