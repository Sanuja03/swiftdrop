package com.swiftdrop.backend.modules.merchant.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.common.security.CurrentCustomerProvider;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.auth.security.CustomAccessDeniedHandler;
import com.swiftdrop.backend.modules.auth.security.CustomAuthenticationEntryPoint;
import com.swiftdrop.backend.modules.auth.security.JwtAuthenticationFilter;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.auth.security.SecurityConfig;
import com.swiftdrop.backend.modules.merchant.controller.MerchantAdminController;
import com.swiftdrop.backend.modules.merchant.controller.MerchantApiController;
import com.swiftdrop.backend.modules.merchant.controller.MerchantController;
import com.swiftdrop.backend.modules.merchant.dto.MerchantProfileResponse;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.merchant.service.MerchantAdminService;
import com.swiftdrop.backend.modules.merchant.service.MerchantOrderService;
import com.swiftdrop.backend.modules.merchant.service.MerchantPricingService;
import com.swiftdrop.backend.modules.merchant.service.MerchantService;
import com.swiftdrop.backend.modules.merchant.service.MerchantWebhookService;
import com.swiftdrop.backend.modules.tracking.controller.CustomerShipmentController;
import com.swiftdrop.backend.modules.tracking.service.CustomerShipmentQueryService;

/**
 * Real security chains (main JWT SecurityConfig + MerchantSecurityConfig):
 * the API key works only on /api/v1/merchant/api/v1/**, the portal needs a merchant JWT,
 * merchant approval is admin-only, and a merchant only ever sees its own parcels.
 */
@WebMvcTest(controllers = {MerchantApiController.class, MerchantController.class, MerchantAdminController.class,
        CustomerShipmentController.class})
@Import({SecurityConfig.class, MerchantSecurityConfig.class, CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class MerchantSecurityTest {
    private static final String VALID_KEY = "sdp_live_test_key";
    private static final String PENDING_KEY = "sdp_live_pending_key";
    private static final UUID OWN_MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000002");
    private static final UUID MERCHANT_USER_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private MerchantRepository merchantRepository;
    @MockitoBean private MerchantOrderService orderService;
    @MockitoBean private MerchantPricingService pricingService;
    @MockitoBean private MerchantService merchantService;
    @MockitoBean private MerchantWebhookService webhookService;
    @MockitoBean private MerchantAdminService adminService;
    @MockitoBean private CurrentMerchantProvider currentMerchantProvider;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private CustomerShipmentQueryService customerShipmentQueryService;
    @MockitoBean private CurrentCustomerProvider currentCustomerProvider;

    private final Merchant ownMerchant = merchant(OWN_MERCHANT_ID, MerchantRegistrationStatus.APPROVED);

    private void keys() {
        when(merchantRepository.findByApiKey(VALID_KEY)).thenReturn(Optional.of(ownMerchant));
        when(merchantRepository.findByApiKey(PENDING_KEY))
                .thenReturn(Optional.of(merchant(OTHER_MERCHANT_ID, MerchantRegistrationStatus.PENDING)));
        when(merchantRepository.findByApiKey("wrong-key")).thenReturn(Optional.empty());
        when(currentMerchantProvider.currentMerchant()).thenReturn(ownMerchant);
        when(orderService.getOrders(any(), any(), any())).thenReturn(List.of());
    }

    // ---- API key on the merchant API -------------------------------------------------------

    @Test
    void validApiKeyCanUseTheMerchantApi() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/merchant/api/v1/orders").header("X-API-KEY", VALID_KEY))
                .andExpect(status().isOk());
        verify(orderService).getOrders(OWN_MERCHANT_ID, null, null);
    }

    @Test
    void missingApiKeyIsUnauthorized() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/merchant/api/v1/orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void invalidApiKeyIsUnauthorized() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/merchant/api/v1/orders").header("X-API-KEY", "wrong-key"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void keyOfAnUnapprovedMerchantIsForbidden() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/merchant/api/v1/orders").header("X-API-KEY", PENDING_KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void merchantJwtAloneDoesNotOpenTheKeyOnlyApi() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/merchant/api/v1/orders")).andExpect(status().isForbidden());
    }

    // ---- API key gives no access anywhere else ---------------------------------------------

    @Test
    void apiKeyDoesNotOpenThePortal() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/merchant/orders").header("X-API-KEY", VALID_KEY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/merchant/api-key/regenerate").header("X-API-KEY", VALID_KEY))
                .andExpect(status().isForbidden());
        verify(merchantService, never()).regenerateApiKey(any());
    }

    @Test
    void apiKeyDoesNotOpenOtherPaths() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/customer/parcels").header("X-API-KEY", VALID_KEY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/merchants").header("X-API-KEY", VALID_KEY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/merchants/" + OTHER_MERCHANT_ID + "/approve").header("X-API-KEY", VALID_KEY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminService, customerShipmentQueryService);
    }

    // ---- Portal (JWT, ROLE_MERCHANT) --------------------------------------------------------

    @Test
    void portalNeedsSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customersCannotUseThePortal() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/orders")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void merchantCannotReadAnotherMerchantsParcels() throws Exception {
        keys();
        mockMvc.perform(get("/api/v1/merchant/orders")
                        .param("merchantId", OTHER_MERCHANT_ID.toString())
                        .header("X-Merchant-Id", OTHER_MERCHANT_ID.toString()))
                .andExpect(status().isOk());

        verify(orderService).getOrders(OWN_MERCHANT_ID, null, null);
        verify(orderService, never()).getOrders(eq(OTHER_MERCHANT_ID), any(), any());
    }

    // ---- Merchant approval (ADMIN only) -----------------------------------------------------

    @Test
    void approvalNeedsSignIn() throws Exception {
        mockMvc.perform(post("/api/v1/admin/merchants/" + OTHER_MERCHANT_ID + "/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void merchantCannotApproveMerchants() throws Exception {
        mockMvc.perform(post("/api/v1/admin/merchants/" + OTHER_MERCHANT_ID + "/approve"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/merchants")).andExpect(status().isForbidden());
        verifyNoInteractions(adminService);
    }

    @Test
    @WithMockUser(roles = {"CUSTOMER"})
    void customerCannotApproveMerchants() throws Exception {
        mockMvc.perform(post("/api/v1/admin/merchants/" + OTHER_MERCHANT_ID + "/approve"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminService);
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatcherCannotApproveMerchants() throws Exception {
        mockMvc.perform(post("/api/v1/admin/merchants/" + OTHER_MERCHANT_ID + "/approve"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanApproveMerchants() throws Exception {
        when(adminService.approveMerchant(eq(OTHER_MERCHANT_ID), any(), any())).thenReturn(
                MerchantProfileResponse.builder().id(OTHER_MERCHANT_ID)
                        .registrationStatus(MerchantRegistrationStatus.APPROVED).build());

        mockMvc.perform(post("/api/v1/admin/merchants/" + OTHER_MERCHANT_ID + "/approve"))
                .andExpect(status().isOk());
    }

    private static Merchant merchant(UUID id, MerchantRegistrationStatus status) {
        return Merchant.builder().id(id).userId(MERCHANT_USER_ID).businessName("Test Shop")
                .registrationStatus(status).build();
    }
}
