package com.swiftdrop.backend.modules.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.backend.modules.merchant.dto.WebhookConfigRequest;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

class MerchantServiceTest {
    private static final UUID USER_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");

    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final ParcelRepository parcelRepository = mock(ParcelRepository.class);
    private final MerchantService service = new MerchantService(merchantRepository, parcelRepository);

    @Test
    void registrationIsLinkedToTheCallersOwnAccountAndStartsPending() {
        when(merchantRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.registerMerchant(USER_ID, registerRequest());

        var saved = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getWebhookSecret()).startsWith("whsec_");
        assertThat(response.getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.PENDING);
        assertThat(response.getApiKey()).isNull();
    }

    @Test
    void secondProfileForTheSameAccountIsRejected() {
        when(merchantRepository.findByUserId(USER_ID)).thenReturn(Optional.of(merchant(null)));

        assertThatThrownBy(() -> service.registerMerchant(USER_ID, registerRequest()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(merchantRepository, never()).save(any());
    }

    @Test
    void regeneratedKeyIsNewAndRandom() {
        var merchant = merchant("sdp_live_old");
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        String first = service.regenerateApiKey(MERCHANT_ID);
        String second = service.regenerateApiKey(MERCHANT_ID);

        assertThat(first).startsWith("sdp_live_").isNotEqualTo("sdp_live_old").isNotEqualTo(second);
        assertThat(merchant.getApiKey()).isEqualTo(second);
    }

    @Test
    void ownProfileShowsTheKeyButTheAdminViewDoesNot() {
        var merchant = merchant("sdp_live_test_key");

        assertThat(service.mapToProfile(merchant).getApiKey()).isEqualTo("sdp_live_test_key");
        assertThat(service.mapToAdminProfile(merchant).getApiKey()).isNull();
    }

    @Test
    void updatesWebhookSettings() {
        var merchant = merchant(null);
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateWebhookConfig(MERCHANT_ID, WebhookConfigRequest.builder()
                .webhookUrl(" https://example.com/webhook ").webhookSecret("whsec_test_secret").build());

        assertThat(response.getWebhookUrl()).isEqualTo("https://example.com/webhook");
        assertThat(response.isWebhookConfigured()).isTrue();
        assertThat(merchant.getWebhookSecret()).isEqualTo("whsec_test_secret");
    }

    @Test
    void statsCountOnlyThisMerchantsParcels() {
        when(parcelRepository.countByMerchantId(MERCHANT_ID)).thenReturn(5L);
        when(parcelRepository.countByMerchantIdAndStatus(MERCHANT_ID, ParcelStatus.CREATED)).thenReturn(1L);
        when(parcelRepository.countByMerchantIdAndStatus(MERCHANT_ID, ParcelStatus.IN_TRANSIT)).thenReturn(2L);
        when(parcelRepository.countByMerchantIdAndStatus(MERCHANT_ID, ParcelStatus.DELIVERED)).thenReturn(1L);
        when(parcelRepository.countByMerchantIdAndStatus(MERCHANT_ID, ParcelStatus.CANCELLED)).thenReturn(1L);

        var stats = service.getStats(MERCHANT_ID);

        assertThat(stats.getTotalOrders()).isEqualTo(5);
        assertThat(stats.getPendingOrders()).isEqualTo(1);
        assertThat(stats.getActiveShipments()).isEqualTo(2);
        assertThat(stats.getCancelledOrders()).isEqualTo(1);
    }

    private static MerchantRegisterRequest registerRequest() {
        return MerchantRegisterRequest.builder()
                .businessName("Test Shop")
                .contactPhone("0000000000")
                .businessAddress("1 Sample Street")
                .build();
    }

    private static Merchant merchant(String apiKey) {
        return Merchant.builder()
                .id(MERCHANT_ID)
                .userId(USER_ID)
                .businessName("Test Shop")
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .apiKey(apiKey)
                .webhookSecret("whsec_old")
                .build();
    }
}
