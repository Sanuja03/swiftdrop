package com.swiftdrop.backend.modules.merchant.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.common.security.NotSignedInException;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.merchant.dto.MerchantApprovalRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.service.MerchantAdminService;

/**
 * Admin review of merchant registrations. Lives under /api/v1/admin/**, so the main
 * SecurityConfig's ADMIN-only rule applies; @PreAuthorize repeats it.
 * The approving admin is always the signed-in user, never an id from the request body.
 */
@RestController
@RequestMapping("/api/v1/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
public class MerchantAdminController {

    private final MerchantAdminService adminService;
    private final UserRepository userRepository;

    public MerchantAdminController(MerchantAdminService adminService, UserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<MerchantProfileResponse>> listMerchants(
            @RequestParam(required = false) MerchantRegistrationStatus status) {
        return ResponseEntity.ok(adminService.listMerchants(status));
    }

    @PostMapping("/{merchantId}/approve")
    public ResponseEntity<MerchantProfileResponse> approveMerchant(
            @PathVariable UUID merchantId,
            @RequestBody(required = false) MerchantApprovalRequest request,
            Principal principal) {
        return ResponseEntity.ok(adminService.approveMerchant(merchantId, notesOf(request), adminId(principal)));
    }

    @PostMapping("/{merchantId}/reject")
    public ResponseEntity<MerchantProfileResponse> rejectMerchant(
            @PathVariable UUID merchantId,
            @RequestBody(required = false) MerchantApprovalRequest request,
            Principal principal) {
        return ResponseEntity.ok(adminService.rejectMerchant(merchantId, notesOf(request), adminId(principal)));
    }

    private static String notesOf(MerchantApprovalRequest request) {
        return request != null ? request.getNotes() : null;
    }

    private UUID adminId(Principal principal) {
        if (principal == null) throw new NotSignedInException();
        return userRepository.findByEmail(principal.getName()).map(User::getId).orElse(null);
    }
}
