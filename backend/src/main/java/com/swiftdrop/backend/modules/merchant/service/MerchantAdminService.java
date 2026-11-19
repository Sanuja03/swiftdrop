package com.swiftdrop.backend.modules.merchant.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;

/**
 * Admin review of merchant registrations. Responses to admins never include a merchant's API key:
 * only the merchant sees it, in their own portal.
 */
@Service
public class MerchantAdminService {

    private static final Logger log = LoggerFactory.getLogger(MerchantAdminService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantService merchantService;

    public MerchantAdminService(MerchantRepository merchantRepository, MerchantService merchantService) {
        this.merchantRepository = merchantRepository;
        this.merchantService = merchantService;
    }

    @Transactional(readOnly = true)
    public List<MerchantProfileResponse> listMerchants(MerchantRegistrationStatus statusFilter) {
        List<Merchant> merchants = statusFilter != null
                ? merchantRepository.findByRegistrationStatus(statusFilter)
                : merchantRepository.findAll();
        return merchants.stream().map(merchantService::mapToAdminProfile).toList();
    }

    /** Approves the merchant and issues an API key if it has none yet. */
    @Transactional
    public MerchantProfileResponse approveMerchant(UUID merchantId, String notes, UUID adminUserId) {
        Merchant merchant = find(merchantId);
        merchant.setRegistrationStatus(MerchantRegistrationStatus.APPROVED);
        merchant.setApprovedBy(adminUserId);
        merchant.setApprovalNotes(notes != null ? notes : "Approved by administrator");
        if (merchant.getApiKey() == null || merchant.getApiKey().isBlank()) {
            merchant.setApiKey(merchantService.newApiKey());
        }
        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant {} approved by admin {}", saved.getId(), adminUserId);
        return merchantService.mapToAdminProfile(saved);
    }

    @Transactional
    public MerchantProfileResponse rejectMerchant(UUID merchantId, String notes, UUID adminUserId) {
        Merchant merchant = find(merchantId);
        merchant.setRegistrationStatus(MerchantRegistrationStatus.REJECTED);
        merchant.setApprovedBy(adminUserId);
        merchant.setApprovalNotes(notes != null ? notes : "Application rejected");
        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant {} rejected by admin {}", saved.getId(), adminUserId);
        return merchantService.mapToAdminProfile(saved);
    }

    private Merchant find(UUID merchantId) {
        return merchantRepository.findById(merchantId).orElseThrow(() -> new MerchantNotFoundException(merchantId));
    }
}
