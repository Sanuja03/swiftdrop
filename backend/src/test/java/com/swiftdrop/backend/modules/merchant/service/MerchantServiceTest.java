package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantStatsResponse;
import com.swiftdrop.backend.modules.merchant.dto.WebhookConfigRequest;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ParcelRepository parcelRepository;

    @InjectMocks
    private MerchantService merchantService;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder()
                .id(merchantId)
                .userId(UUID.randomUUID())
                .businessName("Galle Bookshop")
                .registrationNumber("PV-554433")
                .contactPhone("+94912233445")
                .businessAddress("Fort, Galle")
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .apiKey("sdp_live_old_key")
                .webhookUrl("https://example.com/webhook")
                .webhookSecret("whsec_old_secret")
                .build();
    }

    @Test
    void testRegisterMerchant() {
        UUID userId = UUID.randomUUID();
        MerchantRegisterRequest request = MerchantRegisterRequest.builder()
                .userId(userId)
                .businessName("Kandy Tea Traders")
                .registrationNumber("BR-9988")
                .contactPhone("+94812233445")
                .businessAddress("Dalada Veediya, Kandy")
                .build();

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> {
            Merchant m = i.getArgument(0);
            return Merchant.builder()
                    .id(UUID.randomUUID())
                    .userId(m.getUserId())
                    .businessName(m.getBusinessName())
                    .registrationNumber(m.getRegistrationNumber())
                    .contactPhone(m.getContactPhone())
                    .businessAddress(m.getBusinessAddress())
                    .registrationStatus(m.getRegistrationStatus())
                    .webhookSecret(m.getWebhookSecret())
                    .build();
        });

        MerchantProfileResponse response = merchantService.registerMerchant(request);

        assertNotNull(response);
        assertEquals("Kandy Tea Traders", response.getBusinessName());
        assertEquals(MerchantRegistrationStatus.PENDING, response.getRegistrationStatus());
        assertNull(response.getApiKey()); // API key not generated until approved
    }

    @Test
    void testRegenerateApiKey() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        String newKey = merchantService.regenerateApiKey(merchantId);

        assertNotNull(newKey);
        assertTrue(newKey.startsWith("sdp_live_"));
        assertNotEquals("sdp_live_old_key", newKey);
        verify(merchantRepository).save(merchant);
    }

    @Test
    void testUpdateWebhookConfig() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WebhookConfigRequest req = WebhookConfigRequest.builder()
                .webhookUrl("https://api.merchant.com/swiftdrop-events")
                .webhookSecret("whsec_new_custom_secret")
                .build();

        MerchantProfileResponse response = merchantService.updateWebhookConfig(merchantId, req);

        assertEquals("https://api.merchant.com/swiftdrop-events", response.getWebhookUrl());
        assertTrue(response.isWebhookConfigured());
    }

    @Test
    void testGetStats() {
        when(parcelRepository.countByMerchantId(merchantId)).thenReturn(10L);
        when(parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.CREATED)).thenReturn(2L);
        when(parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.PICKED_UP)).thenReturn(1L);
        when(parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.IN_TRANSIT)).thenReturn(3L);
        when(parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.OUT_FOR_DELIVERY)).thenReturn(1L);
        when(parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.DELIVERED)).thenReturn(2L);
        when(parcelRepository.countByMerchantIdAndStatus(merchantId, ParcelStatus.CANCELLED)).thenReturn(1L);

        MerchantStatsResponse stats = merchantService.getStats(merchantId);

        assertEquals(10L, stats.getTotalOrders());
        assertEquals(2L, stats.getPendingOrders());
        assertEquals(5L, stats.getActiveShipments()); // 1 picked up + 3 in transit + 1 out for delivery
        assertEquals(2L, stats.getDeliveredOrders());
        assertEquals(1L, stats.getCancelledOrders());
    }
}

