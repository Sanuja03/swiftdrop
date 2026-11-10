package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.MerchantApprovalRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantAdminService {

    private static final Logger log = LoggerFactory.getLogger(MerchantAdminService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantService merchantService;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantAdminService(MerchantRepository merchantRepository, MerchantService merchantService) {
        this.merchantRepository = merchantRepository;
        this.merchantService = merchantService;
    }

    @Transactional(readOnly = true)
    public List<MerchantProfileResponse> listMerchants(MerchantRegistrationStatus statusFilter) {
        List<Merchant> merchants;
        if (statusFilter != null) {
            merchants = merchantRepository.findByRegistrationStatus(statusFilter);
        } else {
            merchants = merchantRepository.findAll();
        }
        return merchants.stream().map(merchantService::mapToProfile).toList();
    }

    @Transactional
    public MerchantProfileResponse approveMerchant(UUID merchantId, MerchantApprovalRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        merchant.setRegistrationStatus(MerchantRegistrationStatus.APPROVED);
        merchant.setApprovedBy(request != null ? request.getAdminId() : null);
        merchant.setApprovalNotes(request != null ? request.getNotes() : "Approved by administrator");

        if (merchant.getApiKey() == null || merchant.getApiKey().isBlank()) {
            byte[] keyBytes = new byte[32];
            secureRandom.nextBytes(keyBytes);
            merchant.setApiKey("sdp_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes));
        }

        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant '{}' ({}) APPROVED by admin {}", saved.getBusinessName(), saved.getId(), saved.getApprovedBy());
        return merchantService.mapToProfile(saved);
    }

    @Transactional
    public MerchantProfileResponse rejectMerchant(UUID merchantId, MerchantApprovalRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        merchant.setRegistrationStatus(MerchantRegistrationStatus.REJECTED);
        merchant.setApprovedBy(request != null ? request.getAdminId() : null);
        merchant.setApprovalNotes(request != null ? request.getNotes() : "Application rejected");

        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant '{}' ({}) REJECTED by admin", saved.getBusinessName(), saved.getId());
        return merchantService.mapToProfile(saved);
    }
}
