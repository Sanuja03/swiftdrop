package com.swiftdrop.backend.modules.pricing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.service.PricingService;

@WebMvcTest(PricingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PricingExceptionHandler.class)
class PricingControllerTest {

    /** The auth module's JWT filter is part of every web test slice and needs this bean. */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PricingService pricingService;

    @Test void validEstimateReturnsBreakdown() throws Exception {
        when(pricingService.estimate(any())).thenReturn(new FeeBreakdown(new BigDecimal("590.00"),
                new BigDecimal("0.00"), new BigDecimal("250.00"), new BigDecimal("840.00"), "LKR",
                Instant.parse("2026-11-18T12:30:00Z")));
        mockMvc.perform(post("/api/v1/parcels/estimate").contentType("application/json")
                        .content(request("1", "2", "SMALL", "1", "STANDARD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFee").value(590.00))
                .andExpect(jsonPath("$.weightCharge").value(0.00))
                .andExpect(jsonPath("$.zoneCharge").value(250.00))
                .andExpect(jsonPath("$.totalFee").value(840.00))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.estimatedDeliveryAt").value("2026-11-18T12:30:00Z"));
    }

    @Test void invalidWeightReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/parcels/estimate").contentType("application/json")
                        .content(request("1", "2", "DOCUMENTS", "0.6", "STANDARD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verify(pricingService, never()).estimate(any());
    }

    @Test void unknownZoneReturnsNotFoundProblem() throws Exception {
        when(pricingService.estimate(any())).thenThrow(PricingException.unknownZone(UUID.fromString("30000000-0000-4000-8000-000000000001")));
        mockMvc.perform(post("/api/v1/parcels/estimate").contentType("application/json")
                        .content(request("1", "2", "SMALL", "1", "STANDARD")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Zone not found"));
    }

    private static String request(String pickup, String dropoff, String size, String weight, String service) {
        return """
                {"pickupZoneId":"30000000-0000-4000-8000-%012d","dropoffZoneId":"30000000-0000-4000-8000-%012d","parcelSize":"%s","weightKg":%s,"serviceType":"%s"}
                """.formatted(Integer.parseInt(pickup), Integer.parseInt(dropoff), size, weight, service);
    }
}
