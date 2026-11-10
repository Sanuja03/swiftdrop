package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantStatsResponse;
import com.swiftdrop.backend.modules.merchant.dto.WebhookConfigRequest;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
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
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantService(MerchantRepository merchantRepository, ParcelRepository parcelRepository) {
        this.merchantRepository = merchantRepository;
        this.parcelRepository = parcelRepository;
    }

    @Transactional
    public MerchantProfileResponse registerMerchant(MerchantRegisterRequest request) {
        UUID effectiveUserId = request.getUserId() != null ? request.getUserId() : UUID.randomUUID();

        if (merchantRepository.findByUserId(effectiveUserId).isPresent()) {
            throw new IllegalArgumentException("A merchant profile already exists for this user account.");
        }

        byte[] secretBytes = new byte[24];
        secureRandom.nextBytes(secretBytes);
        String webhookSecret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        Merchant merchant = Merchant.builder()
                .userId(effectiveUserId)
                .businessName(request.getBusinessName())
                .registrationNumber(request.getRegistrationNumber())
                .contactPhone(request.getContactPhone())
                .businessAddress(request.getBusinessAddress())
                .registrationStatus(MerchantRegistrationStatus.PENDING)
                .webhookSecret(webhookSecret)
                .build();

        Merchant saved = merchantRepository.save(merchant);
        log.info("New merchant registered: {} (id={}, status={})", saved.getBusinessName(), saved.getId(), saved.getRegistrationStatus());
        return mapToProfile(saved);
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

        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        String newKey = "sdp_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);

        merchant.setApiKey(newKey);
        merchantRepository.save(merchant);
        log.info("Regenerated API Key for merchant: {}", merchant.getBusinessName());
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

    public MerchantProfileResponse mapToProfile(Merchant m) {
        return MerchantProfileResponse.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .businessName(m.getBusinessName())
                .registrationNumber(m.getRegistrationNumber())
                .contactPhone(m.getContactPhone())
                .businessAddress(m.getBusinessAddress())
                .registrationStatus(m.getRegistrationStatus())
                .apiKey(m.getApiKey())
                .webhookUrl(m.getWebhookUrl())
                .webhookConfigured(m.getWebhookUrl() != null && !m.getWebhookUrl().isBlank())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
