package com.swiftdrop.backend.modules.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantExceptionHandler;
import com.swiftdrop.backend.modules.merchant.security.MerchantApiKeyAuthFilter;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MerchantApiControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MerchantOrderService orderService;

    @Mock
    private MerchantPricingService pricingService;

    private Merchant approvedMerchant;

    @BeforeEach
    void setUp() {
        MerchantApiController controller = new MerchantApiController(orderService, pricingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MerchantExceptionHandler())
                .build();

        approvedMerchant = Merchant.builder()
                .id(UUID.randomUUID())
                .businessName("Test Store")
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .apiKey("sdp_live_test_api_key_valid_123456")
                .build();
    }

    @Test
    void testCreateOrderUnauthorizedWhenApiKeyMissing() throws Exception {
        CreateMerchantOrderRequest req = CreateMerchantOrderRequest.builder()
                .pickupAddress("123 Main St")
                .pickupCity("Colombo")
                .recipientName("John Doe")
                .recipientPhone("0771234567")
                .dropoffAddress("456 Park Ave")
                .dropoffCity("Kandy")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("1.5"))
                .build();

        mockMvc.perform(post("/api/v1/merchant/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateOrderSuccessWithAuthenticatedMerchant() throws Exception {
        CreateMerchantOrderRequest req = CreateMerchantOrderRequest.builder()
                .pickupAddress("123 Main St")
                .pickupCity("Colombo")
                .recipientName("John Doe")
                .recipientPhone("0771234567")
                .dropoffAddress("456 Park Ave")
                .dropoffCity("Kandy")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("1.5"))
                .build();

        MerchantOrderResponse orderResponse = MerchantOrderResponse.builder()
                .id(UUID.randomUUID())
                .trackingNumber("SDP7845621")
                .merchantId(approvedMerchant.getId())
                .status(ParcelStatus.CREATED)
                .statusLabel("Order Created")
                .fee(new BigDecimal("509.00"))
                .build();

        when(orderService.createOrder(eq(approvedMerchant), any())).thenReturn(orderResponse);

        mockMvc.perform(post("/api/v1/merchant/api/v1/orders")
                        .requestAttr(MerchantApiKeyAuthFilter.AUTHENTICATED_MERCHANT_ATTR, approvedMerchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("SDP7845621"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void testCalculateFeePublicEndpoint() throws Exception {
        FeeCalculationRequest feeReq = FeeCalculationRequest.builder()
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("1.0"))
                .pickupCity("Colombo")
                .dropoffCity("Colombo")
                .build();

        when(pricingService.calculateFee(any())).thenReturn(
                com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse.builder()
                        .serviceType(ServiceType.STANDARD)
                        .weightKg(new BigDecimal("1.0"))
                        .baseFee(new BigDecimal("500.00"))
                        .totalFee(new BigDecimal("500.00"))
                        .currency("LKR")
                        .build()
        );

        mockMvc.perform(post("/api/v1/merchant/api/v1/calculate-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.totalFee").value(500.00));
    }
}
