package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.backend.modules.merchant.exception.OrderCannotBeCancelledException;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private MerchantPricingService pricingService;

    @Mock
    private MerchantWebhookService webhookService;

    @InjectMocks
    private MerchantOrderService orderService;

    private Merchant approvedMerchant;
    private Merchant pendingMerchant;

    @BeforeEach
    void setUp() {
        approvedMerchant = Merchant.builder()
                .id(UUID.randomUUID())
                .businessName("Colombo Electronics")
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .apiKey("sdp_live_test123")
                .build();

        pendingMerchant = Merchant.builder()
                .id(UUID.randomUUID())
                .businessName("Pending Merchant")
                .registrationStatus(MerchantRegistrationStatus.PENDING)
                .build();
    }

    @Test
    void testCreateOrderSuccessfully() {
        CreateMerchantOrderRequest request = CreateMerchantOrderRequest.builder()
                .pickupAddress("45 Main Street")
                .pickupCity("Colombo")
                .recipientName("Nimal Silva")
                .recipientPhone("+94771234567")
                .dropoffAddress("12 Kandy Road")
                .dropoffCity("Kadawatha")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("2.5"))
                .build();

        when(pricingService.calculateFee(any())).thenReturn(
                com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse.builder()
                        .totalFee(new BigDecimal("527.00"))
                        .build()
        );

        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> {
            Parcel p = invocation.getArgument(0);
            return Parcel.builder()
                    .id(UUID.randomUUID())
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
                    .build();
        });

        MerchantOrderResponse response = orderService.createOrder(approvedMerchant, request);

        assertNotNull(response);
        assertNotNull(response.getTrackingNumber());
        assertTrue(response.getTrackingNumber().startsWith("SDP"));
        assertEquals(ParcelStatus.CREATED, response.getStatus());
        assertEquals("Colombo Electronics", response.getSenderName());
        assertEquals(new BigDecimal("527.00"), response.getFee());
        assertTrue(response.isCancellable());

        verify(webhookService).notifyStatusChangeAsync(any(), eq("parcel.created"), anyString());
    }

    @Test
    void testCreateOrderThrowsExceptionWhenMerchantNotApproved() {
        CreateMerchantOrderRequest request = CreateMerchantOrderRequest.builder()
                .pickupAddress("45 Main Street")
                .pickupCity("Colombo")
                .recipientName("Nimal Silva")
                .recipientPhone("+94771234567")
                .dropoffAddress("12 Kandy Road")
                .dropoffCity("Kadawatha")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("2.0"))
                .build();

        assertThrows(MerchantNotApprovedException.class, () ->
                orderService.createOrder(pendingMerchant, request));

        verifyNoInteractions(parcelRepository);
    }

    @Test
    void testCancelOrderSuccessfullyWhenStatusIsCreated() {
        String trackingNo = "SDP9988776";
        Parcel parcel = Parcel.builder()
                .id(UUID.randomUUID())
                .trackingNumber(trackingNo)
                .merchantId(approvedMerchant.getId())
                .status(ParcelStatus.CREATED)
                .build();

        when(parcelRepository.findByTrackingNumberAndMerchantId(trackingNo, approvedMerchant.getId()))
                .thenReturn(Optional.of(parcel));
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MerchantOrderResponse response = orderService.cancelOrder(approvedMerchant.getId(), trackingNo);

        assertEquals(ParcelStatus.CANCELLED, response.getStatus());
        assertFalse(response.isCancellable());
        verify(webhookService).notifyStatusChangeAsync(any(), eq("parcel.cancelled"), anyString());
    }

    @Test
    void testCancelOrderThrowsExceptionWhenStatusIsNotCreated() {
        String trackingNo = "SDP9988776";
        Parcel parcel = Parcel.builder()
                .id(UUID.randomUUID())
                .trackingNumber(trackingNo)
                .merchantId(approvedMerchant.getId())
                .status(ParcelStatus.IN_TRANSIT)
                .build();

        when(parcelRepository.findByTrackingNumberAndMerchantId(trackingNo, approvedMerchant.getId()))
                .thenReturn(Optional.of(parcel));

        assertThrows(OrderCannotBeCancelledException.class, () ->
                orderService.cancelOrder(approvedMerchant.getId(), trackingNo));

        verify(parcelRepository, never()).save(any());
    }
}

