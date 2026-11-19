package com.swiftdrop.backend.modules.merchant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantExceptionHandler;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotFoundException;
import com.swiftdrop.backend.modules.merchant.security.CurrentMerchantProvider;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
import com.swiftdrop.backend.modules.merchant.service.MerchantService;
import com.swiftdrop.backend.modules.merchant.service.MerchantWebhookService;

/** The portal always acts for the signed-in merchant; ids sent by the client are ignored. */
class MerchantControllerTest {
    private static final UUID OWN_MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000002");
    private static final UUID OWN_USER_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");

    private final MerchantService merchantService = mock(MerchantService.class);
    private final MerchantOrderService orderService = mock(MerchantOrderService.class);
    private final CurrentMerchantProvider currentMerchantProvider = mock(CurrentMerchantProvider.class);
    private MockMvc mockMvc;
    private Merchant ownMerchant;

    @BeforeEach
    void setUp() {
        var controller = new MerchantController(merchantService, orderService, mock(MerchantPricingService.class),
                mock(MerchantWebhookService.class), currentMerchantProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new MerchantExceptionHandler()).build();
        ownMerchant = Merchant.builder().id(OWN_MERCHANT_ID).userId(OWN_USER_ID).businessName("Test Shop")
                .registrationStatus(MerchantRegistrationStatus.APPROVED).build();
        when(currentMerchantProvider.currentMerchant()).thenReturn(ownMerchant);
    }

    @Test
    void ordersAreAlwaysTheSignedInMerchantsEvenIfAnotherIdIsSent() throws Exception {
        when(orderService.getOrders(OWN_MERCHANT_ID, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/merchant/orders")
                        .param("merchantId", OTHER_MERCHANT_ID.toString())
                        .header("X-Merchant-Id", OTHER_MERCHANT_ID.toString()))
                .andExpect(status().isOk());

        verify(orderService).getOrders(OWN_MERCHANT_ID, null, null);
        verify(orderService, never()).getOrders(eq(OTHER_MERCHANT_ID), any(), any());
    }

    @Test
    void statsAndCancelUseTheSignedInMerchant() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/stats").param("merchantId", OTHER_MERCHANT_ID.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/merchant/orders/SDP0000001/cancel").param("merchantId", OTHER_MERCHANT_ID.toString()))
                .andExpect(status().isOk());

        verify(merchantService).getStats(OWN_MERCHANT_ID);
        verify(orderService).cancelOrder(ownMerchant, "SDP0000001");
    }

    @Test
    void registrationIsForTheSignedInUser() throws Exception {
        var user = User.builder().id(OWN_USER_ID).email("test.merchant@example.com").fullName("Test Merchant").build();
        when(currentMerchantProvider.currentUser()).thenReturn(user);
        when(merchantService.registerMerchant(eq(OWN_USER_ID), any())).thenReturn(MerchantProfileResponse.builder()
                .id(OWN_MERCHANT_ID).userId(OWN_USER_ID).businessName("Test Shop")
                .registrationStatus(MerchantRegistrationStatus.PENDING).build());

        mockMvc.perform(post("/api/v1/merchant/register").contentType("application/json").content("""
                        {"userId":"%s","businessName":"Test Shop","contactPhone":"0000000000","businessAddress":"1 Sample Street"}
                        """.formatted(OTHER_MERCHANT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(OWN_USER_ID.toString()));

        verify(merchantService).registerMerchant(eq(OWN_USER_ID), any());
    }

    @Test
    void accountWithoutMerchantProfileGets404() throws Exception {
        when(currentMerchantProvider.currentMerchant())
                .thenThrow(new MerchantNotFoundException("No merchant profile exists for this account yet."));

        mockMvc.perform(get("/api/v1/merchant/profile")).andExpect(status().isNotFound());
        verify(merchantService, never()).mapToProfile(isNull());
    }
}
