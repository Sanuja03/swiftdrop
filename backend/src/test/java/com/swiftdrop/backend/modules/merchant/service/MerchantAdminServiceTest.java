package com.swiftdrop.backend.modules.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;

class MerchantAdminServiceTest {
    private static final UUID MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("70000000-0000-4000-8000-000000000009");

    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final MerchantService merchantService = new MerchantService(merchantRepository, mock(ParcelRepository.class));
    private final MerchantAdminService service = new MerchantAdminService(merchantRepository, merchantService);

    @BeforeEach
    void saveReturnsTheEntity() {
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvalRecordsTheSignedInAdminAndIssuesAKey() {
        var merchant = pending();
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        var response = service.approveMerchant(MERCHANT_ID, "Looks good", ADMIN_ID);

        assertThat(merchant.getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.APPROVED);
        assertThat(merchant.getApprovedBy()).isEqualTo(ADMIN_ID);
        assertThat(merchant.getApiKey()).startsWith("sdp_live_");
        assertThat(response.getApiKey()).as("admins never see a merchant's key").isNull();
    }

    @Test
    void reapprovalKeepsTheExistingKey() {
        var merchant = pending();
        merchant.setApiKey("sdp_live_existing");
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        service.approveMerchant(MERCHANT_ID, null, ADMIN_ID);

        assertThat(merchant.getApiKey()).isEqualTo("sdp_live_existing");
    }

    @Test
    void rejectionRecordsTheAdmin() {
        var merchant = pending();
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        service.rejectMerchant(MERCHANT_ID, "Missing documents", ADMIN_ID);

        assertThat(merchant.getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.REJECTED);
        assertThat(merchant.getApprovedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void listHidesApiKeys() {
        var merchant = pending();
        merchant.setApiKey("sdp_live_secret");
        when(merchantRepository.findAll()).thenReturn(List.of(merchant));

        assertThat(service.listMerchants(null)).allMatch(profile -> profile.getApiKey() == null);
    }

    @Test
    void unknownMerchantIsNotFound() {
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveMerchant(MERCHANT_ID, null, ADMIN_ID))
                .isInstanceOf(MerchantNotFoundException.class);
    }

    private static Merchant pending() {
        return Merchant.builder()
                .id(MERCHANT_ID)
                .userId(UUID.fromString("70000000-0000-4000-8000-000000000001"))
                .businessName("Test Shop")
                .registrationStatus(MerchantRegistrationStatus.PENDING)
                .build();
    }
}
