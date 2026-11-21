package com.swiftdrop.backend.modules.pricing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.AdminPricingRuleResponse;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.service.PricingRuleAdminService;

@WebMvcTest(AdminPricingRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PricingExceptionHandler.class)
class AdminPricingRuleControllerTest {
    private static final AdminPricingRuleResponse STANDARD = new AdminPricingRuleResponse(ServiceType.STANDARD,
            new BigDecimal("590.00"), new BigDecimal("100.00"), new BigDecimal("250.00"), true, "LKR", null);
    private static final String BODY = "{\"baseFee\":590,\"perKgFee\":100,\"interZoneFee\":250,\"active\":true}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PricingRuleAdminService pricingRuleAdminService;
    /** The auth module's JWT filter is part of every web test slice and needs this bean. */
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test void listsRules() throws Exception {
        when(pricingRuleAdminService.listAll()).thenReturn(List.of(STANDARD));
        mockMvc.perform(get("/api/v1/admin/pricing-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceType").value("STANDARD"))
                .andExpect(jsonPath("$[0].baseFee").value(590.00));
    }

    @Test void updatesRule() throws Exception {
        when(pricingRuleAdminService.update(eq(ServiceType.STANDARD), any())).thenReturn(STANDARD);
        mockMvc.perform(put("/api/v1/admin/pricing-rules/STANDARD").contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interZoneFee").value(250.00));
    }

    @Test void negativeFeeIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/pricing-rules/STANDARD").contentType("application/json")
                        .content("{\"baseFee\":-5,\"perKgFee\":100,\"interZoneFee\":250,\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verify(pricingRuleAdminService, never()).update(any(), any());
    }

    @Test void missingFieldIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/pricing-rules/STANDARD").contentType("application/json")
                        .content("{\"baseFee\":590,\"perKgFee\":100,\"interZoneFee\":250}"))
                .andExpect(status().isBadRequest());
    }

    @Test void unknownServiceTypeIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/admin/pricing-rules/OVERNIGHT").contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test void missingRuleIsNotFound() throws Exception {
        when(pricingRuleAdminService.update(eq(ServiceType.EXPRESS), any()))
                .thenThrow(PricingException.ruleNotFound(ServiceType.EXPRESS));
        mockMvc.perform(put("/api/v1/admin/pricing-rules/EXPRESS").contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test void deactivatingLastActiveServiceIsConflict() throws Exception {
        when(pricingRuleAdminService.update(eq(ServiceType.STANDARD), any()))
                .thenThrow(PricingException.lastActiveService());
        mockMvc.perform(put("/api/v1/admin/pricing-rules/STANDARD").contentType("application/json")
                        .content("{\"baseFee\":590,\"perKgFee\":100,\"interZoneFee\":250,\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("At least one service must stay active"));
    }
}
