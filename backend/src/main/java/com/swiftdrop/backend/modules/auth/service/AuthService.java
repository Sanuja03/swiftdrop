package com.swiftdrop.backend.modules.auth.service;

import com.swiftdrop.backend.modules.auth.dto.*;
import com.swiftdrop.backend.modules.auth.entity.*;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.exception.InvalidTokenException;
import com.swiftdrop.backend.modules.auth.exception.UserAlreadyExistsException;
import com.swiftdrop.backend.modules.auth.repository.RefreshTokenRepository;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenDurationMs;

    @Transactional
    public AuthResponse register(SignUpRequest request) {
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
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user [{}] with role [{}] and status [{}]", 
                savedUser.getEmail(), savedUser.getRole(), savedUser.getStatus());

        // If merchant is pending approval, do not issue active JWT
        if (savedUser.getStatus() == AccountStatus.PENDING_APPROVAL) {
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
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getStatus() == AccountStatus.PENDING_APPROVAL) {
            throw new AccountNotActiveException("Your merchant account is pending admin approval.");
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

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        String token = request.getIdToken().trim();
        String email;
        String name;
        String sub;

        // Support demo/mock tokens for offline development and tests
        if (token.startsWith("mock-google-token-")) {
            String suffix = token.replace("mock-google-token-", "");
            email = suffix + "@gmail.com";
            name = "Google User (" + suffix + ")";
            sub = "mock-google-sub-" + suffix;
        } else {
            try {
                RestClient restClient = RestClient.create();
                @SuppressWarnings("unchecked")
                Map<String, Object> tokenInfo = restClient.get()
                        .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", token)
                        .retrieve()
                        .body(Map.class);

                if (tokenInfo == null || !tokenInfo.containsKey("email")) {
                    throw new BadCredentialsException("Invalid Google token or email not found in token");
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
                    .build();
            return userRepository.save(newUser);
        });

        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new AccountNotActiveException("Your account has been suspended. Please contact customer support.");
        }

        if (user.getProvider() == null || user.getProvider() == AuthProvider.LOCAL) {
            user.setProvider(AuthProvider.GOOGLE);
            user.setProviderId(sub);
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

    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token does not exist"));

        if (token.isRevoked() || token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new InvalidTokenException("Refresh token was expired or revoked");
        }

        User user = token.getUser();
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
