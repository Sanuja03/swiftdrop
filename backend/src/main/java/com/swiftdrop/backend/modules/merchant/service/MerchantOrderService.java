package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.backend.modules.merchant.exception.OrderCannotBeCancelledException;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

@Service
public class MerchantOrderService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOrderService.class);

    private final ParcelRepository parcelRepository;
    private final MerchantPricingService pricingService;
    private final MerchantWebhookService webhookService;
    private final Random random = new SecureRandom();

    public MerchantOrderService(ParcelRepository parcelRepository,
                                MerchantPricingService pricingService,
                                MerchantWebhookService webhookService) {
        this.parcelRepository = parcelRepository;
        this.pricingService = pricingService;
        this.webhookService = webhookService;
    }

    @Transactional
    public MerchantOrderResponse createOrder(Merchant merchant, CreateMerchantOrderRequest request) {
        if (!merchant.isApproved()) {
            throw new MerchantNotApprovedException(
                    "Merchant account '" + merchant.getBusinessName() + "' is currently " + merchant.getRegistrationStatus()
                            + ". Only APPROVED merchants can book delivery orders.");
        }

        FeeCalculationResponse feeResponse = pricingService.calculateFee(FeeCalculationRequest.builder()
                .serviceType(request.getServiceType())
                .weightKg(request.getWeightKg())
                .pickupCity(request.getPickupCity())
                .dropoffCity(request.getDropoffCity())
                .build());

        String trackingNumber = generateUniqueTrackingNumber();
        Instant estimatedDelivery = pricingService.estimateDeliveryDate(request.getServiceType());

        String senderName = (request.getSenderName() != null && !request.getSenderName().isBlank())
                ? request.getSenderName()
                : merchant.getBusinessName();

        Parcel parcel = Parcel.builder()
                .trackingNumber(trackingNumber)
                .merchantId(merchant.getId())
                .senderName(senderName)
                .pickupAddress(request.getPickupAddress())
                .pickupCity(request.getPickupCity())
                .recipientName(request.getRecipientName())
                .recipientPhone(request.getRecipientPhone())
                .dropoffAddress(request.getDropoffAddress())
                .dropoffCity(request.getDropoffCity())
                .serviceType(request.getServiceType())
                .weightKg(request.getWeightKg())
                .fee(feeResponse.getTotalFee())
                .status(ParcelStatus.CREATED)
                .estimatedDeliveryAt(estimatedDelivery)
                .build();

        Parcel saved = parcelRepository.save(parcel);
        log.info("Merchant {} created order tracking# {}", merchant.getBusinessName(), saved.getTrackingNumber());

        webhookService.notifyStatusChangeAsync(saved, "parcel.created", "Delivery order booked by merchant");

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MerchantOrderResponse> getOrders(UUID merchantId, ParcelStatus statusFilter, String search) {
        List<Parcel> parcels;
        if (statusFilter != null) {
            parcels = parcelRepository.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, statusFilter);
        } else {
            parcels = parcelRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
        }

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            parcels = parcels.stream().filter(p ->
                    p.getTrackingNumber().toLowerCase().contains(q)
                            || p.getRecipientName().toLowerCase().contains(q)
                            || (p.getRecipientPhone() != null && p.getRecipientPhone().contains(q))
                            || p.getDropoffCity().toLowerCase().contains(q)
            ).toList();
        }

        return parcels.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public MerchantOrderResponse getOrder(UUID merchantId, String trackingNumber) {
        Parcel parcel = parcelRepository.findByTrackingNumberAndMerchantId(trackingNumber, merchantId)
                .orElseThrow(() -> new NoSuchElementException("Order not found with tracking number: " + trackingNumber));
        return mapToResponse(parcel);
    }

    @Transactional
    public MerchantOrderResponse cancelOrder(UUID merchantId, String trackingNumber) {
        Parcel parcel = parcelRepository.findByTrackingNumberAndMerchantId(trackingNumber, merchantId)
                .orElseThrow(() -> new NoSuchElementException("Order not found with tracking number: " + trackingNumber));

        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new OrderCannotBeCancelledException(trackingNumber, parcel.getStatus());
        }

        parcel.changeStatus(ParcelStatus.CANCELLED);
        Parcel updated = parcelRepository.save(parcel);
        log.info("Merchant {} cancelled order tracking# {}", merchantId, trackingNumber);

        webhookService.notifyStatusChangeAsync(updated, "parcel.cancelled", "Order cancelled by merchant");

        return mapToResponse(updated);
    }

    public String generateUniqueTrackingNumber() {
        for (int i = 0; i < 10; i++) {
            int num = 1000000 + random.nextInt(9000000);
            String candidate = "SDP" + num;
            if (parcelRepository.findByTrackingNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "SDP" + (System.currentTimeMillis() % 10000000);
    }

    public MerchantOrderResponse mapToResponse(Parcel p) {
        return MerchantOrderResponse.builder()
                .id(p.getId())
                .trackingNumber(p.getTrackingNumber())
                .merchantId(p.getMerchantId())
                .senderName(p.getSenderName())
                .pickupAddress(p.getPickupAddress())
                .pickupCity(p.getPickupCity())
                .recipientName(p.getRecipientName())
                .recipientPhone(p.getRecipientPhone())
                .dropoffAddress(p.getDropoffAddress())
                .dropoffCity(p.getDropoffCity())
                .serviceType(p.getServiceType())
                .weightKg(p.getWeightKg())
                .fee(p.getFee())
                .status(p.getStatus())
                .statusLabel(p.getStatus().getLabel())
                .estimatedDeliveryAt(p.getEstimatedDeliveryAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .cancellable(p.getStatus() == ParcelStatus.CREATED)
                .build();
    }
}
