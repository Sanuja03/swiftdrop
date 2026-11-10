package com.swiftdrop.backend.modules.merchant.service;

import com.swiftdrop.backend.modules.merchant.dto.MerchantApprovalRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantAdminServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    private MerchantService merchantService;
    private MerchantAdminService adminService;

    private UUID merchantId;
    private Merchant pendingMerchant;

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, null);
        adminService = new MerchantAdminService(merchantRepository, merchantService);

        merchantId = UUID.randomUUID();
        pendingMerchant = Merchant.builder()
                .id(merchantId)
                .userId(UUID.randomUUID())
                .businessName("Lanka Spices Co.")
                .registrationStatus(MerchantRegistrationStatus.PENDING)
                .build();
    }

    @Test
    void testApproveMerchantGeneratesApiKeyAndSetsApproved() {
        UUID adminId = UUID.randomUUID();
        MerchantApprovalRequest req = MerchantApprovalRequest.builder()
                .adminId(adminId)
                .notes("All legal documents verified")
                .build();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(pendingMerchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        MerchantProfileResponse response = adminService.approveMerchant(merchantId, req);

        assertEquals(MerchantRegistrationStatus.APPROVED, response.getRegistrationStatus());
        assertNotNull(response.getApiKey());
        assertTrue(response.getApiKey().startsWith("sdp_live_"));
        verify(merchantRepository).save(argThat(m ->
                m.getRegistrationStatus() == MerchantRegistrationStatus.APPROVED
                        && m.getApprovedBy().equals(adminId)
        ));
    }

    @Test
    void testRejectMerchantSetsRejectedStatus() {
        MerchantApprovalRequest req = MerchantApprovalRequest.builder()
                .notes("Business registration certificate invalid")
                .build();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(pendingMerchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        MerchantProfileResponse response = adminService.rejectMerchant(merchantId, req);

        assertEquals(MerchantRegistrationStatus.REJECTED, response.getRegistrationStatus());
        assertNull(response.getApiKey());
    }
}
