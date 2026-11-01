package com.swiftdrop.backend.modules.auth.service;

import com.swiftdrop.backend.modules.auth.dto.AuthResponse;
import com.swiftdrop.backend.modules.auth.dto.LoginRequest;
import com.swiftdrop.backend.modules.auth.dto.SignUpRequest;
import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.exception.UserAlreadyExistsException;
import com.swiftdrop.backend.modules.auth.repository.RefreshTokenRepository;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenDurationMs", 604800000L);
    }

    @Test
    @DisplayName("Should successfully register a new customer as ACTIVE")
    void registerCustomerSuccess() {
        SignUpRequest request = SignUpRequest.builder()
                .email("customer@swiftdrop.com")
                .password("password123")
                .fullName("John Doe")
                .phone("0771234567")
                .role(Role.ROLE_CUSTOMER)
                .build();

        when(userRepository.existsByEmail("customer@swiftdrop.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("customer@swiftdrop.com")
                .passwordHash("encoded-password")
                .fullName("John Doe")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateAccessToken(savedUser)).thenReturn("mock-access-token");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        RefreshToken savedToken = RefreshToken.builder()
                .token("mock-refresh-token")
                .user(savedUser)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals(Role.ROLE_CUSTOMER, response.getUser().getRole());
        assertEquals(AccountStatus.ACTIVE, response.getUser().getStatus());
    }

    @Test
    @DisplayName("Should register merchant as PENDING_APPROVAL without issuing active tokens")
    void registerMerchantPendingApproval() {
        SignUpRequest request = SignUpRequest.builder()
                .email("merchant@store.com")
                .password("password123")
                .fullName("Partner Store")
                .role(Role.ROLE_MERCHANT)
                .build();

        when(userRepository.existsByEmail("merchant@store.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("merchant@store.com")
                .passwordHash("encoded-password")
                .fullName("Partner Store")
                .role(Role.ROLE_MERCHANT)
                .status(AccountStatus.PENDING_APPROVAL)
                .provider(AuthProvider.LOCAL)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertNull(response.getAccessToken());
        assertEquals(AccountStatus.PENDING_APPROVAL, response.getUser().getStatus());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Should throw exception if email is already taken")
    void registerDuplicateEmailThrowsException() {
        SignUpRequest request = SignUpRequest.builder()
                .email("existing@swiftdrop.com")
                .password("password123")
                .fullName("Jane Doe")
                .build();

        when(userRepository.existsByEmail("existing@swiftdrop.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully login active user and return JWT tokens")
    void loginSuccess() {
        LoginRequest request = LoginRequest.builder()
                .email("user@swiftdrop.com")
                .password("password123")
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@swiftdrop.com")
                .passwordHash("hashed-pw")
                .fullName("Active User")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("user@swiftdrop.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-pw")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("valid-jwt");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        RefreshToken savedToken = RefreshToken.builder()
                .token("mock-refresh-token")
                .user(user)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("valid-jwt", response.getAccessToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void loginWrongPasswordThrowsBadCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("user@swiftdrop.com")
                .password("wrongpassword")
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@swiftdrop.com")
                .passwordHash("hashed-pw")
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("user@swiftdrop.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "hashed-pw")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Should reject login if merchant is still PENDING_APPROVAL")
    void loginPendingMerchantThrowsAccountNotActive() {
        LoginRequest request = LoginRequest.builder()
                .email("merchant@store.com")
                .password("correctpassword")
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("merchant@store.com")
                .passwordHash("hashed-pw")
                .role(Role.ROLE_MERCHANT)
                .status(AccountStatus.PENDING_APPROVAL)
                .build();

        when(userRepository.findByEmail("merchant@store.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpassword", "hashed-pw")).thenReturn(true);

        assertThrows(AccountNotActiveException.class, () -> authService.login(request));
    }
}

