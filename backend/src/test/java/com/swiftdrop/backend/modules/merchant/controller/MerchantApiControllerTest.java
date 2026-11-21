package com.swiftdrop.backend.modules.merchant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationResponse;
import com.swiftdrop.backend.modules.merchant.dto.MerchantOrderResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantExceptionHandler;
import com.swiftdrop.backend.modules.merchant.exception.OrderCannotBeCancelledException;
import com.swiftdrop.backend.modules.merchant.security.CurrentMerchantProvider;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

class MerchantApiControllerTest {

    private static final UUID MERCHANT_ID = UUID.fromString("90000000-0000-4000-8000-000000000001");

    private final MerchantOrderService orderService = mock(MerchantOrderService.class);
    private final MerchantPricingService pricingService = mock(MerchantPricingService.class);
    private final CurrentMerchantProvider currentMerchantProvider = mock(CurrentMerchantProvider.class);

    private MockMvc mockMvc;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        var controller = new MerchantApiController(orderService, pricingService, currentMerchantProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MerchantExceptionHandler())
                .build();

        merchant = Merchant.builder()
                .id(MERCHANT_ID)
                .businessName("Apex Tech Store")
                .registrationStatus(MerchantRegistrationStatus.APPROVED)
                .apiKey("sdp_live_testkey123456789")
                .build();

        when(currentMerchantProvider.currentMerchant()).thenReturn(merchant);
    }

    @Test
    void createOrderProgrammaticSuccess() throws Exception {
        MerchantOrderResponse orderResponse = MerchantOrderResponse.builder()
                .trackingNumber("SDP-20261120-ABC123")
                .status(ParcelStatus.CREATED)
                .senderName("Apex Tech Store")
                .recipientName("John Silva")
                .fee(new BigDecimal("650.00"))
                .build();

        when(orderService.createOrder(eq(merchant), any())).thenReturn(orderResponse);

        String payload = """
                {
                    "recipientName": "John Silva",
                    "recipientPhone": "+94771234567",
                    "recipientEmail": "john@example.com",
                    "pickupAddress": "123 Galle Rd, Colombo",
                    "dropoffAddress": "45 Peradeniya Rd, Kandy",
                    "pickupCity": "Colombo",
                    "dropoffCity": "Kandy",
                    "weightKg": 2.5,
                    "serviceType": "EXPRESS"
                }
                """;

        mockMvc.perform(post("/api/v1/merchant/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("SDP-20261120-ABC123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.fee").value(650.00));

        verify(orderService, times(1)).createOrder(eq(merchant), any());
    }

    @Test
    void createOrderIdempotencyPreventsDuplicateOrders() throws Exception {
        MerchantOrderResponse orderResponse = MerchantOrderResponse.builder()
                .trackingNumber("SDP-20261120-XYZ999")
                .status(ParcelStatus.CREATED)
                .recipientName("Alice Perera")
                .fee(new BigDecimal("500.00"))
                .build();

        when(orderService.createOrder(eq(merchant), any())).thenReturn(orderResponse);

        String payload = """
                {
                    "recipientName": "Alice Perera",
                    "recipientPhone": "+94779876543",
                    "pickupAddress": "10 Kandy Rd, Colombo",
                    "dropoffAddress": "25 Station Rd, Gampaha",
                    "pickupCity": "Colombo",
                    "dropoffCity": "Gampaha",
                    "weightKg": 1.0,
                    "serviceType": "STANDARD"
                }
                """;

        // First call with Idempotency-Key
        mockMvc.perform(post("/api/v1/merchant/api/v1/orders")
                        .header("Idempotency-Key", "idem-uuid-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("SDP-20261120-XYZ999"));

        // Second call with identical Idempotency-Key returns cached response with 200 OK
        mockMvc.perform(post("/api/v1/merchant/api/v1/orders")
                        .header("Idempotency-Key", "idem-uuid-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SDP-20261120-XYZ999"));

        // Order service createOrder should only be invoked once
        verify(orderService, times(1)).createOrder(eq(merchant), any());
    }

    @Test
    void cancelOrderReturns409ConflictWhenOrderIsAlreadyPickedUp() throws Exception {
        String trackingNumber = "SDP-20261120-DEF456";
        when(orderService.cancelOrder(merchant, trackingNumber))
                .thenThrow(new OrderCannotBeCancelledException(trackingNumber, ParcelStatus.PICKED_UP));

        mockMvc.perform(post("/api/v1/merchant/api/v1/orders/" + trackingNumber + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cancellation is blocked to protect physical inventory")));

        verify(orderService).cancelOrder(merchant, trackingNumber);
    }

    @Test
    void calculateFeeReturnsZoneBreakdown() throws Exception {
        FeeCalculationResponse feeResponse = FeeCalculationResponse.builder()
                .baseFee(new BigDecimal("350.00"))
                .weightCharge(new BigDecimal("100.00"))
                .zoneSurcharge(new BigDecimal("150.00"))
                .totalFee(new BigDecimal("600.00"))
                .pickupCity("Colombo")
                .dropoffCity("Kandy")
                .estimatedDeliveryTime("1-2 business days")
                .currency("LKR")
                .build();

        when(pricingService.calculateFee(any())).thenReturn(feeResponse);

        String payload = """
                {
                    "pickupCity": "Colombo",
                    "dropoffCity": "Kandy",
                    "weightKg": 2.0,
                    "serviceType": "STANDARD"
                }
                """;

        mockMvc.perform(post("/api/v1/merchant/api/v1/calculate-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFee").value(600.00))
                .andExpect(jsonPath("$.pickupCity").value("Colombo"))
                .andExpect(jsonPath("$.dropoffCity").value("Kandy"));
    }

    @Test
    void getOrderAndListOrders() throws Exception {
        String trackingNumber = "SDP-20261120-ORD123";
        MerchantOrderResponse order = MerchantOrderResponse.builder()
                .trackingNumber(trackingNumber)
                .status(ParcelStatus.IN_TRANSIT)
                .recipientName("Saman Kumara")
                .build();

        when(orderService.getOrder(MERCHANT_ID, trackingNumber)).thenReturn(order);
        when(orderService.getOrders(MERCHANT_ID, ParcelStatus.IN_TRANSIT, null)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/v1/merchant/api/v1/orders/" + trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        mockMvc.perform(get("/api/v1/merchant/api/v1/orders").param("status", "IN_TRANSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber").value(trackingNumber));
    }
}
