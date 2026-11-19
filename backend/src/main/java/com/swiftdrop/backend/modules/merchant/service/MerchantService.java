package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantStatsResponse;
import com.swiftdrop.backend.modules.merchant.dto.WebhookConfigRequest;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.merchant.event.MerchantRegisteredPayload;
import com.swiftdrop.backend.modules.messaging.service.OutboxService;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class MerchantService {

    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantRepository merchantRepository;
    private final ParcelRepository parcelRepository;
    private final OutboxService outboxService;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantService(MerchantRepository merchantRepository, ParcelRepository parcelRepository,
                           OutboxService outboxService) {
        this.merchantRepository = merchantRepository;
        this.parcelRepository = parcelRepository;
        this.outboxService = outboxService;
    }

    /**
     * Creates a PENDING merchant profile for the signed-in user's own account. The user id always
     * comes from the authenticated principal, never from the request.
     */
    @Transactional
    public MerchantProfileResponse registerMerchant(UUID userId, MerchantRegisterRequest request) {
        if (request.getRegistrationNumber() == null || request.getRegistrationNumber().isBlank()
                || request.getRegistrationNumber().trim().length() > 100) {
            throw new IllegalArgumentException("A business registration number of 1 to 100 characters is required");
        }
        if (merchantRepository.findByUserId(userId).isPresent()) {
            throw new IllegalArgumentException("A merchant profile already exists for this user account.");
        }

        Merchant merchant = Merchant.builder()
                .userId(userId)
                .businessName(request.getBusinessName())
                .registrationNumber(request.getRegistrationNumber().trim())
                .contactPhone(request.getContactPhone())
                .businessAddress(request.getBusinessAddress())
                .registrationStatus(MerchantRegistrationStatus.PENDING)
                .webhookSecret(newWebhookSecret())
                .build();

        Merchant saved = merchantRepository.save(merchant);
        outboxService.publishEvent("MERCHANT", saved.getId(), MerchantRegisteredPayload.EVENT_TYPE,
                MerchantRegisteredPayload.ROUTING_KEY, new MerchantRegisteredPayload(saved.getId()));
        log.info("New merchant registered (id={}, status={})", saved.getId(), saved.getRegistrationStatus());
        return mapToProfile(saved);
    }

    /** A new random API key. The only place keys are generated (approval, regeneration, dev seed). */
    public String newApiKey() {
        return "sdp_live_" + randomToken(32);
    }

    /** A new random webhook signing secret. */
    public String newWebhookSecret() {
        return "whsec_" + randomToken(24);
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    @Transactional(readOnly = true)
    public MerchantProfileResponse getProfile(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
        return mapToProfile(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantProfileResponse getProfileByUserId(UUID userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found for user: " + userId));
        return mapToProfile(merchant);
    }

    @Transactional(readOnly = true)
    public Merchant getMerchantByApiKey(String apiKey) {
        return merchantRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new IllegalArgumentException("Invalid API Key"));
    }

    @Transactional
    public MerchantProfileResponse updateWebhookConfig(UUID merchantId, WebhookConfigRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        merchant.setWebhookUrl(request.getWebhookUrl().trim());
        if (request.getWebhookSecret() != null && !request.getWebhookSecret().isBlank()) {
            merchant.setWebhookSecret(request.getWebhookSecret().trim());
        }

        Merchant updated = merchantRepository.save(merchant);
        log.info("Updated webhook configuration for merchant: {}", updated.getBusinessName());
        return mapToProfile(updated);
    }

    @Transactional
    public String regenerateApiKey(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        String newKey = newApiKey();
        merchant.setApiKey(newKey);
        merchantRepository.save(merchant);
        log.info("Regenerated API key for merchant {}", merchant.getId());
        return newKey;
    }

    @Transactional(readOnly = true)
    public MerchantStatsResponse getStats(UUID merchantId) {
        long total = parcelRepository.countByMerchantId(merchantId);
        long pending = parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.CREATED);
        long pickedUp = parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.PICKED_UP);
        long inTransit = parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.IN_TRANSIT);
        long outForDelivery = parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.OUT_FOR_DELIVERY);
        long delivered = parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.DELIVERED);
        long cancelled = parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.CANCELLED);

        long active = pickedUp + inTransit + outForDelivery;

        return MerchantStatsResponse.builder()
                .totalOrders(total)
                .activeShipments(active)
                .deliveredOrders(delivered)
                .pendingOrders(pending)
                .cancelledOrders(cancelled)
                .build();
    }

    /** The merchant's own view of their profile (includes their API key). */
    public MerchantProfileResponse mapToProfile(Merchant m) {
        return toProfile(m, true);
    }

    /** What admins see: the same profile without the merchant's API key. */
    public MerchantProfileResponse mapToAdminProfile(Merchant m) {
        return toProfile(m, false);
    }

    private MerchantProfileResponse toProfile(Merchant m, boolean includeApiKey) {
        return MerchantProfileResponse.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .businessName(m.getBusinessName())
                .registrationNumber(m.getRegistrationNumber())
                .contactPhone(m.getContactPhone())
                .businessAddress(m.getBusinessAddress())
                .approvalNotes(m.getApprovalNotes())
                .registrationStatus(m.getRegistrationStatus())
                .apiKey(includeApiKey ? m.getApiKey() : null)
                .webhookUrl(m.getWebhookUrl())
                .webhookConfigured(m.getWebhookUrl() != null && !m.getWebhookUrl().isBlank())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
