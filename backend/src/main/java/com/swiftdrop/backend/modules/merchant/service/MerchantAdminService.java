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
import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin review of merchant registrations. Responses to admins never include a merchant's API key:
 * only the merchant sees it, in their own portal.
 */
@Service
public class MerchantAdminService {

    private static final Logger log = LoggerFactory.getLogger(MerchantAdminService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantService merchantService;
    private final UserRepository userRepository;
    private final com.swiftdrop.backend.modules.messaging.service.OutboxService outboxService;

    public MerchantAdminService(MerchantRepository merchantRepository, MerchantService merchantService,
                                UserRepository userRepository,
                                com.swiftdrop.backend.modules.messaging.service.OutboxService outboxService) {
        this.merchantRepository = merchantRepository;
        this.merchantService = merchantService;
        this.userRepository = userRepository;
        this.outboxService = outboxService;
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
        boolean reapproval = merchant.getRegistrationStatus() == MerchantRegistrationStatus.REJECTED;
        User user = applicant(merchant, false);
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Suspended merchant accounts cannot be activated by approval");
        }
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
        merchant.setRegistrationStatus(MerchantRegistrationStatus.APPROVED);
        merchant.setApprovedBy(adminUserId);
        merchant.setApprovalNotes(notes != null ? notes : "Approved by administrator");
        if (reapproval || merchant.getApiKey() == null || merchant.getApiKey().isBlank()) {
            merchant.setApiKey(merchantService.newApiKey());
        }
        Merchant saved = merchantRepository.save(merchant);
        outboxService.publishEvent("MERCHANT", saved.getId(),
                com.swiftdrop.backend.modules.merchant.event.MerchantApprovedPayload.EVENT_TYPE,
                com.swiftdrop.backend.modules.merchant.event.MerchantApprovedPayload.ROUTING_KEY,
                new com.swiftdrop.backend.modules.merchant.event.MerchantApprovedPayload(
                        saved.getId(), saved.getUserId(), saved.getBusinessName()));
        log.info("Merchant {} approved by admin {}", saved.getId(), adminUserId);
        return merchantService.mapToAdminProfile(saved);
    }

    @Transactional
    public MerchantProfileResponse rejectMerchant(UUID merchantId, String notes, UUID adminUserId) {
        if (notes == null || notes.isBlank() || notes.trim().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason of 1 to 2000 characters is required");
        }
        Merchant merchant = find(merchantId);
        User user = applicant(merchant, true);
        if (user.getStatus() != AccountStatus.SUSPENDED) {
            user.setStatus(AccountStatus.REJECTED);
            userRepository.save(user);
        }
        merchant.setRegistrationStatus(MerchantRegistrationStatus.REJECTED);
        merchant.setApiKey(null);
        merchant.setApprovedBy(adminUserId);
        merchant.setApprovalNotes(notes.trim());
        Merchant saved = merchantRepository.save(merchant);
        outboxService.publishEvent("MERCHANT", saved.getId(),
                com.swiftdrop.backend.modules.merchant.event.MerchantRejectedPayload.EVENT_TYPE,
                com.swiftdrop.backend.modules.merchant.event.MerchantRejectedPayload.ROUTING_KEY,
                new com.swiftdrop.backend.modules.merchant.event.MerchantRejectedPayload(
                        saved.getId(), saved.getUserId(), saved.getBusinessName(), saved.getApprovalNotes()));
        log.info("Merchant {} rejected by admin {}", saved.getId(), adminUserId);
        return merchantService.mapToAdminProfile(saved);
    }

    private Merchant find(UUID merchantId) {
        return merchantRepository.findById(merchantId).orElseThrow(() -> new MerchantNotFoundException(merchantId));
    }

    private User applicant(Merchant merchant, boolean rejection) {
        if (merchant.getRegistrationStatus() != MerchantRegistrationStatus.PENDING
                && !(rejection && merchant.getRegistrationStatus() == MerchantRegistrationStatus.APPROVED)
                && !(!rejection && merchant.getRegistrationStatus() == MerchantRegistrationStatus.REJECTED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, rejection
                    ? "Only pending or approved merchant applications can be rejected"
                    : "Only pending or rejected merchant applications can be approved");
        }
        return userRepository.findById(merchant.getUserId())
                .filter(user -> user.getRole() == Role.ROLE_MERCHANT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Merchant applicant account is missing or invalid"));
    }
}
