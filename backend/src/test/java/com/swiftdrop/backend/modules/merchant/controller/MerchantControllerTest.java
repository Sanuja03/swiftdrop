package com.swiftdrop.backend.modules.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantRegisterRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantStatsResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantExceptionHandler;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
import com.swiftdrop.backend.modules.merchant.service.MerchantService;
import com.swiftdrop.backend.modules.merchant.service.MerchantWebhookService;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchantControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MerchantService merchantService;

    @Mock
    private MerchantOrderService orderService;

    @Mock
    private MerchantPricingService pricingService;

    @Mock
    private MerchantWebhookService webhookService;

    @Mock
    private MerchantRepository merchantRepository;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        MerchantController controller = new MerchantController(
                merchantService, orderService, pricingService, webhookService, merchantRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MerchantExceptionHandler())
                .build();

        merchantId = UUID.randomUUID();
        merchant = Merchant.builder()
                .id(merchantId)
                .businessName("Galle Gems & Craft")
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .build();
    }

    @Test
    void testRegisterMerchant() throws Exception {
        MerchantRegisterRequest req = MerchantRegisterRequest.builder()
                .businessName("Lanka Organic Spices")
                .contactPhone("0112345678")
                .businessAddress("10 Flower Road, Colombo")
                .build();

        MerchantProfileResponse res = MerchantProfileResponse.builder()
                .id(UUID.randomUUID())
                .businessName("Lanka Organic Spices")
                .registrationStatus(MerchantRegistrationStatus.PENDING)
                .build();

        when(merchantService.registerMerchant(any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/merchant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessName").value("Lanka Organic Spices"))
                .andExpect(jsonPath("$.registrationStatus").value("PENDING"));
    }

    @Test
    void testGetProfile() throws Exception {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantService.getProfile(merchantId)).thenReturn(
                MerchantProfileResponse.builder()
                        .id(merchantId)
                        .businessName("Galle Gems & Craft")
                        .registrationStatus(MerchantRegistrationStatus.APPROVED)
                        .build()
        );

        mockMvc.perform(get("/api/v1/merchant/profile")
                        .param("merchantId", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Galle Gems & Craft"));
    }

    @Test
    void testGetStats() throws Exception {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantService.getStats(merchantId)).thenReturn(
                MerchantStatsResponse.builder()
                        .totalOrders(15)
                        .activeShipments(5)
                        .deliveredOrders(8)
                        .pendingOrders(2)
                        .build()
        );

        mockMvc.perform(get("/api/v1/merchant/stats")
                        .param("merchantId", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(15))
                .andExpect(jsonPath("$.activeShipments").value(5));
    }

    @Test
    void testCancelOrder() throws Exception {
        String trackingNo = "SDP5566778";
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderService.cancelOrder(merchantId, trackingNo)).thenReturn(
                MerchantOrderResponse.builder()
                        .trackingNumber(trackingNo)
                        .status(ParcelStatus.CANCELLED)
                        .statusLabel("Cancelled")
                        .cancellable(false)
                        .build()
        );

        mockMvc.perform(post("/api/v1/merchant/orders/{trackingNumber}/cancel", trackingNo)
                        .param("merchantId", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}

