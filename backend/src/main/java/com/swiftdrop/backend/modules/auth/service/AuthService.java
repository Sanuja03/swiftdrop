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
import com.swiftdrop.backend.modules.merchant.service.MerchantService;
import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String MERCHANT_REJECTED_MESSAGE = "Your merchant application has been rejected. Please check your email for the rejection reason or contact SwiftDrop support.";
    private static final String MERCHANT_PENDING_MESSAGE = "Your merchant application is pending admin approval. You can sign in after it is approved.";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final MerchantService merchantService;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenDurationMs;

    /**
     * Accepts "mock-google-token-&lt;name&gt;" as a Google sign-in for name@gmail.com. Off unless a test
     * turns it on: anyone could otherwise sign in as any Gmail address.
     */
    @Value("${app.auth.google.mock-tokens-enabled:false}")
    private boolean mockGoogleTokensEnabled;

    @Value("${app.auth.google.client-id:}")
    private String googleClientId;

    @Transactional
    public AuthResponse register(SignUpRequest request) {
        if (!request.isSelfServiceRole()) {
            throw new AccessDeniedException("Staff accounts cannot be created through public signup");
        }
        if (!request.isMerchantDetailsComplete()) {
            throw new IllegalArgumentException("Merchant signup requires business name, business registration number, contact phone and business address");
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Email is already registered: " + normalizedEmail);
        }

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.ROLE_CUSTOMER;

        // Merchants require an Admin approval step before becoming ACTIVE
        AccountStatus initialStatus = (assignedRole == Role.ROLE_MERCHANT)
                ? AccountStatus.PENDING_APPROVAL
                : AccountStatus.ACTIVE;

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .role(assignedRole)
                .status(initialStatus)
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        if (assignedRole == Role.ROLE_MERCHANT) {
            merchantService.registerMerchant(savedUser.getId(), MerchantRegisterRequest.builder()
                    .businessName(request.getBusinessName().trim())
                    .registrationNumber(request.getRegistrationNumber())
                    .contactPhone(request.getPhone().trim())
                    .businessAddress(request.getBusinessAddress().trim())
                    .build());
        }
        log.info("Registered new user [{}] with role [{}] and status [{}], emailVerified=[false]", 
                savedUser.getEmail(), savedUser.getRole(), savedUser.getStatus());

        // Generate and send 6-digit email verification code
        int code = 100000 + new SecureRandom().nextInt(900000);
        String verificationCodeStr = String.valueOf(code);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(savedUser)
                .token(verificationCodeStr)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .verified(false)
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        emailService.sendEmailVerification(savedUser.getEmail(), savedUser.getFullName(), verificationCodeStr);

        // Always require email verification before issuing JWT token
        return AuthResponse.builder()
                .user(UserProfileDto.fromEntity(savedUser))
                .accessToken(null)
                .refreshToken(null)
                .expiresIn(0)
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getStatus() == AccountStatus.PENDING_APPROVAL && isRejectedMerchant(user)) {
            throw new AccountNotActiveException(MERCHANT_REJECTED_MESSAGE);
        }

        if (!user.isEmailVerified()) {
            throw new AccountNotActiveException("Your email address is not verified yet. Please check your inbox or click 'Verify Email' to activate your account.");
        }

        if (user.getStatus() == AccountStatus.PENDING_APPROVAL) {
            throw new AccountNotActiveException(MERCHANT_PENDING_MESSAGE);
        }

        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountNotActiveException("Your account has been suspended. Please contact customer support.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("User logged in successfully: [{}]", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                .user(UserProfileDto.fromEntity(user))
                .build();
    }

    private boolean isRejectedMerchant(User user) {
        if (user.getRole() != Role.ROLE_MERCHANT) return false;
        try {
            var profile = merchantService.getProfileByUserId(user.getId());
            return profile != null && profile.getRegistrationStatus()
                    == com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus.REJECTED;
        } catch (com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException missingProfile) {
            return false;
        }
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        String token = request.getIdToken().trim();
        String email;
        String name;
        String sub;

        // Support test/mock tokens for hermetic offline testing
        if (mockGoogleTokensEnabled && token.startsWith("mock-google-token-")) {
            String suffix = token.replace("mock-google-token-", "");
            email = suffix + "@gmail.com";
            name = "Google User (" + suffix + ")";
            sub = "mock-google-sub-" + suffix;
        } else {
            if (googleClientId == null || googleClientId.isBlank()) {
                log.error("Google Sign-In attempted while app.auth.google.client-id (GOOGLE_CLIENT_ID) is not configured");
                throw new BadCredentialsException("Google Sign-In is not configured on the server");
            }
            try {
                Map<String, Object> tokenInfo = fetchGoogleTokenInfo(token);

                if (tokenInfo == null || !tokenInfo.containsKey("email")) {
                    throw new BadCredentialsException("Invalid Google token or email not found in token");
                }

                Object audClaim = tokenInfo.get("aud");
                String tokenAudience = audClaim != null ? audClaim.toString().trim() : "";
                if (!googleClientId.trim().equals(tokenAudience)) {
                    log.warn("Rejected Google token with unexpected audience");
                    throw new BadCredentialsException("Google token was not issued for this application");
                }

                Object emailVerifiedClaim = tokenInfo.get("email_verified");
                if (emailVerifiedClaim != null && !"true".equalsIgnoreCase(emailVerifiedClaim.toString())) {
                    throw new BadCredentialsException("Google account email is not verified");
                }

                email = (String) tokenInfo.get("email");
                name = (String) tokenInfo.getOrDefault("name", email);
                sub = (String) tokenInfo.get("sub");
            } catch (BadCredentialsException bce) {
                throw bce;
            } catch (Exception e) {
                log.error("Failed to verify Google token: {}", e.getMessage());
                throw new BadCredentialsException("Invalid or expired Google authentication token");
            }
        }

        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail).orElseGet(() -> {
            log.info("Creating new user account from Google OAuth: {}", normalizedEmail);
            User newUser = User.builder()
                    .email(normalizedEmail)
                    .fullName(name != null ? name : normalizedEmail)
                    .role(Role.ROLE_CUSTOMER)
                    .status(AccountStatus.ACTIVE)
                    .provider(AuthProvider.GOOGLE)
                    .providerId(sub)
                    .emailVerified(true)
                    .build();
            return userRepository.save(newUser);
        });

        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountNotActiveException("Your account has been suspended. Please contact customer support.");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(isRejectedMerchant(user) ? MERCHANT_REJECTED_MESSAGE : MERCHANT_PENDING_MESSAGE);
        }

        if (user.getProvider() == null || user.getProvider() == AuthProvider.LOCAL) {
            user.setProvider(AuthProvider.GOOGLE);
            user.setProviderId(sub);
            user.setEmailVerified(true);
            userRepository.save(user);
        } else if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("Google OAuth login successful for [{}]", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                .user(UserProfileDto.fromEntity(user))
                .build();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> fetchGoogleTokenInfo(String idToken) {
        return RestClient.create().get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                .retrieve()
                .body(Map.class);
    }

    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token does not exist"));

        if (token.isRevoked() || token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new InvalidTokenException("Refresh token was expired or revoked");
        }

        User user = token.getUser();
        if (user.getStatus() != AccountStatus.ACTIVE || !user.isEmailVerified()) {
            throw new AccountNotActiveException("Email verification and an active account are required");
        }
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(token.getToken())
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                .user(UserProfileDto.fromEntity(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return UserProfileDto.fromEntity(user);
    }

    @Transactional
    public UserProfileDto updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }

        User updated = userRepository.save(user);
        log.info("Updated profile for user [{}]", updated.getEmail());
        return UserProfileDto.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public List<UserProfileDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserProfileDto::fromEntity)
                .toList();
    }

    @Transactional
    public UserProfileDto updateUserStatus(UUID userId, AccountStatus newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        user.setStatus(newStatus);
        User updated = userRepository.save(user);
        return UserProfileDto.fromEntity(updated);
    }

    @Transactional
    public ForgotPasswordResponse processForgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("No account found with this email address. Please check your email or create a new account.");
        }

        User user = userOpt.get();
        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new InvalidTokenException("This account is registered with Google Sign-In. Please sign in using Google.");
        }

        // Clean up any previous reset tokens for this user
        passwordResetTokenRepository.deleteByUser(user);

        // Generate 6-digit verification code
        int code = 100000 + new SecureRandom().nextInt(900000);
        String resetTokenStr = String.valueOf(code);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(resetTokenStr)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);
        log.info("Password reset code generated for user [{}]", normalizedEmail);

        // Dispatch real email to user inbox
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetTokenStr);

        return ForgotPasswordResponse.builder()
                .success(true)
                .message("A 6-digit verification code has been sent to your email. Enter it below with your new password.")
                .resetCode(null)
                .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String cleanToken = request.getToken().trim();
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findFirstByTokenAndUsedFalseOrderByCreatedAtDesc(cleanToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid or already used verification code."));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("This verification code has expired. Please request a new code.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Security: revoke any active refresh tokens to force fresh login with new password
        refreshTokenRepository.deleteByUser(user);
        log.info("Password reset successfully for user [{}]", user.getEmail());
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        String cleanEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + cleanEmail));

        // Block password changes for OAuth / Google accounts
        if (user.getProvider() == AuthProvider.GOOGLE || user.getPasswordHash() == null) {
            throw new IllegalArgumentException("Password change is only available for accounts registered with email and password.");
        }

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("The current password you entered is incorrect.");
        }

        // Verify new password and confirm password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation password do not match.");
        }

        // Ensure new password is not identical to current password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be the same as your current password.");
        }

        // Update password hash
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke active refresh tokens so existing sessions must refresh or re-authenticate
        refreshTokenRepository.deleteByUser(user);
        log.info("Password changed successfully for user [{}]", user.getEmail());
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String code = request.getCode().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with this email address"));

        if (user.isEmailVerified()) {
            log.info("Email [{}] is already verified", normalizedEmail);
            if (user.getStatus() != AccountStatus.ACTIVE) {
                return AuthResponse.builder().user(UserProfileDto.fromEntity(user))
                        .accessToken(null).refreshToken(null).expiresIn(0).build();
            }
            String accessToken = jwtTokenProvider.generateAccessToken(user);
            RefreshToken refreshToken = createRefreshToken(user);
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                    .user(UserProfileDto.fromEntity(user))
                    .build();
        }

        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findFirstByUserAndTokenAndVerifiedFalseOrderByCreatedAtDesc(user, code)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification code. Please check your email or request a new code."));

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Verification code has expired. Please click 'Resend Verification Code'.");
        }

        verificationToken.setVerified(true);
        emailVerificationTokenRepository.save(verificationToken);

        user.setEmailVerified(true);
        User savedUser = userRepository.save(user);
        log.info("Email successfully verified for user [{}]", normalizedEmail);

        // If merchant is pending admin approval, do not issue active JWT
        if (savedUser.getStatus() != AccountStatus.ACTIVE) {
            return AuthResponse.builder()
                    .user(UserProfileDto.fromEntity(savedUser))
                    .accessToken(null)
                    .refreshToken(null)
                    .expiresIn(0)
                    .build();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(savedUser);
        RefreshToken refreshToken = createRefreshToken(savedUser);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationMs())
                .user(UserProfileDto.fromEntity(savedUser))
                .build();
    }

    @Transactional
    public void resendVerificationCode(ResendVerificationRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with this email address"));

        if (user.isEmailVerified()) {
            throw new InvalidTokenException("This email address is already verified. You can log in directly.");
        }

        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new InvalidTokenException("Google accounts do not require email verification. Please sign in with Google.");
        }

        emailVerificationTokenRepository.deleteByUser(user);

        int code = 100000 + new SecureRandom().nextInt(900000);
        String tokenStr = String.valueOf(code);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(tokenStr)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .verified(false)
                .build();

        emailVerificationTokenRepository.save(verificationToken);
        emailService.sendEmailVerification(user.getEmail(), user.getFullName(), tokenStr);
        log.info("Resent email verification code to [{}]", user.getEmail());
    }

    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(refreshTokenRepository::deleteByUser);
    }

    private RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }
}
