package com.swiftdrop.backend.modules.auth.service;

import com.swiftdrop.backend.modules.auth.dto.*;
import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.exception.InvalidTokenException;
import com.swiftdrop.backend.modules.auth.exception.UserAlreadyExistsException;
import com.swiftdrop.backend.modules.auth.repository.EmailVerificationTokenRepository;
import com.swiftdrop.backend.modules.auth.repository.PasswordResetTokenRepository;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenDurationMs", 604800000L);
    }

    @Test
    @DisplayName("Should successfully register a new customer with pending email verification")
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
                .emailVerified(false)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertNull(response.getAccessToken());
        assertFalse(response.getUser().isEmailVerified());
        assertEquals(Role.ROLE_CUSTOMER, response.getUser().getRole());
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendEmailVerification(eq("customer@swiftdrop.com"), any(), any());
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
                .emailVerified(true)
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

    @Test
    @DisplayName("Should successfully authenticate new user via Google OAuth")
    void loginWithGoogleNewUserSuccess() {
        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("mock-google-token-alice")
                .build();

        when(userRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.empty());

        User createdUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@gmail.com")
                .fullName("Google User (alice)")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.GOOGLE)
                .providerId("mock-google-sub-alice")
                .build();

        when(userRepository.save(any(User.class))).thenReturn(createdUser);
        when(jwtTokenProvider.generateAccessToken(createdUser)).thenReturn("google-jwt-token");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        RefreshToken savedToken = RefreshToken.builder()
                .token("mock-google-refresh-token")
                .user(createdUser)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        AuthResponse response = authService.loginWithGoogle(request);

        assertNotNull(response);
        assertEquals("google-jwt-token", response.getAccessToken());
        assertEquals("mock-google-refresh-token", response.getRefreshToken());
        assertEquals("alice@gmail.com", response.getUser().getEmail());
        assertEquals(AuthProvider.GOOGLE, response.getUser().getProvider());
    }

    @Test
    @DisplayName("Should successfully authenticate existing user via Google OAuth")
    void loginWithGoogleExistingUserSuccess() {
        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("mock-google-token-bob")
                .build();

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("bob@gmail.com")
                .fullName("Bob Existing")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        when(userRepository.findByEmail("bob@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(jwtTokenProvider.generateAccessToken(existingUser)).thenReturn("bob-jwt-token");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        RefreshToken savedToken = RefreshToken.builder()
                .token("bob-refresh-token")
                .user(existingUser)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        AuthResponse response = authService.loginWithGoogle(request);

        assertNotNull(response);
        assertEquals("bob-jwt-token", response.getAccessToken());
        assertEquals("bob-refresh-token", response.getRefreshToken());
        assertEquals(AuthProvider.GOOGLE, existingUser.getProvider());
    }

    @Test
    @DisplayName("Should successfully update user profile details")
    void updateProfileSuccess() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Risinu Updated")
                .phone("0771234567")
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@gmail.com")
                .fullName("Original Name")
                .phone(null)
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .provider(AuthProvider.GOOGLE)
                .build();

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = authService.updateProfile("user@gmail.com", request);

        assertNotNull(updated);
        assertEquals("Risinu Updated", updated.getFullName());
        assertEquals("0771234567", updated.getPhone());
    }

    @Test
    @DisplayName("Should successfully generate 6-digit password reset code for local user")
    void forgotPasswordSuccess() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("customer@swiftdrop.com");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("customer@swiftdrop.com")
                .provider(AuthProvider.LOCAL)
                .build();

        when(userRepository.findByEmail("customer@swiftdrop.com")).thenReturn(Optional.of(user));

        ForgotPasswordResponse response = authService.processForgotPassword(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNull(response.getResetCode());
        verify(passwordResetTokenRepository).deleteByUser(user);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("customer@swiftdrop.com"), any(), any());
    }

    @Test
    @DisplayName("Should reject password reset for Google OAuth user with InvalidTokenException")
    void forgotPasswordGoogleUserThrowsException() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("googleuser@gmail.com");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("googleuser@gmail.com")
                .provider(AuthProvider.GOOGLE)
                .build();

        when(userRepository.findByEmail("googleuser@gmail.com")).thenReturn(Optional.of(user));

        assertThrows(InvalidTokenException.class, () -> authService.processForgotPassword(request));
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when email is not registered")
    void forgotPasswordNonExistentUserThrowsUsernameNotFound() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> authService.processForgotPassword(request));
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully reset password with valid token")
    void resetPasswordSuccess() {
        ResetPasswordRequest request = new ResetPasswordRequest("123456", "newSecretPassword99");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("customer@swiftdrop.com")
                .passwordHash("oldHash")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token("123456")
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();

        when(passwordResetTokenRepository.findFirstByTokenAndUsedFalseOrderByCreatedAtDesc("123456"))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newSecretPassword99")).thenReturn("newEncodedHash");

        authService.resetPassword(request);

        assertTrue(token.isUsed());
        assertEquals("newEncodedHash", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("Should throw InvalidTokenException when reset token is expired")
    void resetPasswordExpiredTokenThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("654321", "newPassword");
        User user = User.builder().id(UUID.randomUUID()).email("user@test.com").build();

        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .user(user)
                .token("654321")
                .expiryDate(LocalDateTime.now().minusMinutes(5))
                .used(false)
                .build();

        when(passwordResetTokenRepository.findFirstByTokenAndUsedFalseOrderByCreatedAtDesc("654321"))
                .thenReturn(Optional.of(expiredToken));

        assertThrows(InvalidTokenException.class, () -> authService.resetPassword(request));
    }

    @Test
    @DisplayName("Should throw InvalidTokenException when reset token is not found or already used")
    void resetPasswordInvalidTokenThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("000000", "newPassword");
        when(passwordResetTokenRepository.findFirstByTokenAndUsedFalseOrderByCreatedAtDesc("000000"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.resetPassword(request));
    }

    @Test
    @DisplayName("Should throw AccountNotActiveException when attempting login with unverified email")
    void loginUnverifiedEmailThrowsAccountNotActiveException() {
        LoginRequest request = LoginRequest.builder()
                .email("unverified@swiftdrop.com")
                .password("password123")
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("unverified@swiftdrop.com")
                .passwordHash("hashed-pw")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .emailVerified(false)
                .build();

        when(userRepository.findByEmail("unverified@swiftdrop.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-pw")).thenReturn(true);

        assertThrows(AccountNotActiveException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Should successfully verify email with valid 6-digit code")
    void verifyEmailSuccess() {
        VerifyEmailRequest request = new VerifyEmailRequest("user@swiftdrop.com", "456789");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@swiftdrop.com")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .emailVerified(false)
                .build();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .token("456789")
                .expiryDate(LocalDateTime.now().plusHours(2))
                .verified(false)
                .build();

        when(userRepository.findByEmail("user@swiftdrop.com")).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findFirstByUserAndTokenAndVerifiedFalseOrderByCreatedAtDesc(user, "456789"))
                .thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any(User.class))).thenReturn("new-jwt-token");
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);

        RefreshToken savedToken = RefreshToken.builder().token("mock-refresh").user(user).build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        AuthResponse response = authService.verifyEmail(request);

        assertNotNull(response);
        assertEquals("new-jwt-token", response.getAccessToken());
        assertTrue(user.isEmailVerified());
        assertTrue(token.isVerified());
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    @DisplayName("Should throw InvalidTokenException when verification code is invalid")
    void verifyEmailInvalidCodeThrowsException() {
        VerifyEmailRequest request = new VerifyEmailRequest("user@swiftdrop.com", "000000");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@swiftdrop.com")
                .emailVerified(false)
                .build();

        when(userRepository.findByEmail("user@swiftdrop.com")).thenReturn(Optional.of(user));
        when(emailVerificationTokenRepository.findFirstByUserAndTokenAndVerifiedFalseOrderByCreatedAtDesc(user, "000000"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.verifyEmail(request));
    }

    @Test
    @DisplayName("Should successfully resend verification code")
    void resendVerificationCodeSuccess() {
        ResendVerificationRequest request = new ResendVerificationRequest("user@swiftdrop.com");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@swiftdrop.com")
                .fullName("User Test")
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();

        when(userRepository.findByEmail("user@swiftdrop.com")).thenReturn(Optional.of(user));

        authService.resendVerificationCode(request);

        verify(emailVerificationTokenRepository).deleteByUser(user);
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendEmailVerification(eq("user@swiftdrop.com"), eq("User Test"), any());
    }
}

