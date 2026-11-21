package com.swiftdrop.backend.modules.notifications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.swiftdrop.backend.modules.auth.security.*;
import com.swiftdrop.backend.modules.zone.controller.AdminZoneController;
import com.swiftdrop.backend.modules.zone.service.ZoneAdminService;
import com.swiftdrop.backend.modules.pricing.controller.AdminPricingRuleController;
import com.swiftdrop.backend.modules.pricing.service.PricingRuleAdminService;
import com.swiftdrop.backend.modules.merchant.controller.MerchantAdminController;
import com.swiftdrop.backend.modules.merchant.service.MerchantAdminService;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;

/** Thenuri's integration boundary checks; teammate implementation/test files are unchanged. */
@WebMvcTest(controllers = {AdminZoneController.class, AdminPricingRuleController.class, MerchantAdminController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, CustomAccessDeniedHandler.class, CustomAuthenticationEntryPoint.class})
class AdminWorkspaceAccessTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private JwtTokenProvider jwt;
    @MockitoBean private ZoneAdminService zones;
    @MockitoBean private PricingRuleAdminService prices;
    @MockitoBean private MerchantAdminService merchants;
    @MockitoBean private UserRepository users;

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatchersCannotListApproveOrRejectMerchants() throws Exception {
        mvc.perform(get("/api/v1/admin/merchants")).andExpect(status().isForbidden());
        for (String action : new String[]{"approve", "reject"}) {
            mvc.perform(post("/api/v1/admin/merchants/40000000-0000-4000-8000-000000000001/" + action)
                    .contentType("application/json").content("{\"notes\":\"test\"}"))
                    .andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verifyNoInteractions(merchants);
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatchersCannotReadOrWriteZonesAndPricing() throws Exception {
        mvc.perform(get("/api/v1/admin/zones")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/zones").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/zones/40000000-0000-4000-8000-000000000001/active")
                .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/pricing-rules")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/pricing-rules/STANDARD").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
}
