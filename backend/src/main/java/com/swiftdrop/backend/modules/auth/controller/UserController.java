package com.swiftdrop.backend.modules.auth.controller;

import com.swiftdrop.backend.modules.auth.dto.UserProfileDto;
import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    // Accessible by any authenticated user
    @GetMapping("/users/me")
    public ResponseEntity<UserProfileDto> getCurrentUserProfile(Principal principal) {
        UserProfileDto profile = authService.getCurrentUser(principal.getName());
        return ResponseEntity.ok(profile);
    }

    // Admin / Dispatcher only: List all users
    @GetMapping("/admin/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public ResponseEntity<List<UserProfileDto>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    // Admin only: Approve or suspend a user (e.g. merchant approval)
    @PatchMapping("/admin/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileDto> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam AccountStatus status) {
        return ResponseEntity.ok(authService.updateUserStatus(userId, status));
    }

    // Courier only test endpoint
    @GetMapping("/courier/test")
    @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<Map<String, String>> courierEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Authorized access to Courier portal"));
    }

    // Merchant only test endpoint
    @GetMapping("/merchant/test")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<Map<String, String>> merchantEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Authorized access to Merchant portal"));
    }

    // Customer only test endpoint
    @GetMapping("/customer/test")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, String>> customerEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Authorized access to Customer portal"));
    }
}
