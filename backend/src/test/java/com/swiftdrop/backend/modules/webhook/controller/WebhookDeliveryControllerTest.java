package com.swiftdrop.backend.modules.webhook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.modules.auth.security.CustomAccessDeniedHandler;
import com.swiftdrop.backend.modules.auth.security.CustomAuthenticationEntryPoint;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.auth.security.SecurityConfig;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery;
import com.swiftdrop.backend.modules.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.service.WebhookDeliveryService;

/**
 * Runs against the application's real SecurityConfig, so these are the actual role rules.
 */
@WebMvcTest(WebhookDeliveryController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        WebhookExceptionHandler.class})
class WebhookDeliveryControllerTest {

    private static final String MERCHANT_EMAIL = "orders@kandy-crafts.lk";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookDeliveryService deliveryService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(username = MERCHANT_EMAIL, roles = "MERCHANT")
    void merchantListsTheirOwnDeliveries() throws Exception {
        WebhookDelivery delivery = delivery();
        when(deliveryService.listForMerchant(eq(MERCHANT_EMAIL), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(delivery)));

        mockMvc.perform(get(WebhookDeliveryController.MERCHANT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(delivery.getId().toString()))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SDP7845621"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].maxAttempts").value(6))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = MERCHANT_EMAIL, roles = "MERCHANT")
    void pageSizeIsCappedAndNewestComesFirst() throws Exception {
        when(deliveryService.listForMerchant(eq(MERCHANT_EMAIL), eq(WebhookDeliveryStatus.DEAD), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(WebhookDeliveryController.MERCHANT_PATH).param("status", "DEAD").param("size", "5000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryService).listForMerchant(eq(MERCHANT_EMAIL), eq(WebhookDeliveryStatus.DEAD), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    @WithMockUser(username = MERCHANT_EMAIL, roles = "MERCHANT")
    void merchantCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(get(WebhookDeliveryController.ADMIN_PATH))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(WebhookDeliveryController.ADMIN_PATH + "/" + UUID.randomUUID() + "/retry"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(username = "customer@gmail.com", roles = "CUSTOMER")
    void customerCannotSeeWebhookDeliveries() throws Exception {
        mockMvc.perform(get(WebhookDeliveryController.MERCHANT_PATH))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(username = "courier@swiftdrop.lk", roles = "COURIER")
    void courierCannotSeeWebhookDeliveries() throws Exception {
        mockMvc.perform(get(WebhookDeliveryController.ADMIN_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get(WebhookDeliveryController.MERCHANT_PATH))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(WebhookDeliveryController.ADMIN_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@swiftdrop.lk", roles = "ADMIN")
    void adminListsAllDeliveriesWithFilters() throws Exception {
        UUID merchantId = UUID.randomUUID();
        when(deliveryService.listAll(eq(merchantId), eq(WebhookDeliveryStatus.RETRYING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(delivery())));

        mockMvc.perform(get(WebhookDeliveryController.ADMIN_PATH)
                        .param("merchantId", merchantId.toString())
                        .param("status", "RETRYING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @WithMockUser(username = "dispatch@swiftdrop.lk", roles = "DISPATCHER")
    void dispatcherCanViewDeliveryLog() throws Exception {
        when(deliveryService.listAll(isNull(), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(WebhookDeliveryController.ADMIN_PATH))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@swiftdrop.lk", roles = "ADMIN")
    void adminRetriesDeadDelivery() throws Exception {
        WebhookDelivery delivery = delivery();
        when(deliveryService.retryAsAdmin(delivery.getId())).thenReturn(delivery);

        mockMvc.perform(post(WebhookDeliveryController.ADMIN_PATH + "/" + delivery.getId() + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(delivery.getId().toString()));
    }

    @Test
    @WithMockUser(username = MERCHANT_EMAIL, roles = "MERCHANT")
    void someoneElsesDeliveryLooksLikeItDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(deliveryService.getForMerchant(MERCHANT_EMAIL, id)).thenThrow(WebhookException.deliveryNotFound(id));

        mockMvc.perform(get(WebhookDeliveryController.MERCHANT_PATH + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Webhook delivery not found"));
    }

    @Test
    @WithMockUser(username = MERCHANT_EMAIL, roles = "MERCHANT")
    void retryingADeliveryThatIsNotDeadIsAConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(deliveryService.retryForMerchant(MERCHANT_EMAIL, id)).thenThrow(WebhookException.notRetryable());

        mockMvc.perform(post(WebhookDeliveryController.MERCHANT_PATH + "/" + id + "/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Delivery cannot be retried"));
    }

    private static WebhookDelivery delivery() {
        return WebhookDelivery.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SDP7845621",
                "parcel.status_changed", "https://shop.example.lk/hook", "{}", 6);
    }
}
