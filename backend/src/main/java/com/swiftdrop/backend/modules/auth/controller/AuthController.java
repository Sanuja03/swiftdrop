package com.swiftdrop.backend.modules.auth.controller;

import com.swiftdrop.backend.modules.auth.dto.*;
import com.swiftdrop.backend.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignUpRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileDto profile = authService.getCurrentUser(principal.getName());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserProfileDto updated = authService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        if (principal != null) {
            authService.logout(principal.getName());
        }
        return ResponseEntity.noContent().build();
    }
}

