package com.swiftdrop.backend.modules.merchant.controller;

import com.swiftdrop.backend.modules.merchant.dto.MerchantApprovalRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.service.MerchantAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for Admin / Dispatcher actions on Merchant accounts
 * (e.g., review pending registrations, approve, and reject).
 */
@RestController
@RequestMapping("/api/v1/admin/merchants")
public class MerchantAdminController {

    private static final Logger log = LoggerFactory.getLogger(MerchantAdminController.class);

    private final MerchantAdminService adminService;

    public MerchantAdminController(MerchantAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<MerchantProfileResponse>> listMerchants(
            @RequestParam(required = false) MerchantRegistrationStatus status) {
        return ResponseEntity.ok(adminService.listMerchants(status));
    }

    @PostMapping("/{merchantId}/approve")
    public ResponseEntity<MerchantProfileResponse> approveMerchant(
            @PathVariable UUID merchantId,
            @RequestBody(required = false) MerchantApprovalRequest request) {
        MerchantProfileResponse response = adminService.approveMerchant(merchantId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{merchantId}/reject")
    public ResponseEntity<MerchantProfileResponse> rejectMerchant(
            @PathVariable UUID merchantId,
            @RequestBody(required = false) MerchantApprovalRequest request) {
        MerchantProfileResponse response = adminService.rejectMerchant(merchantId, request);
        return ResponseEntity.ok(response);
    }
}
