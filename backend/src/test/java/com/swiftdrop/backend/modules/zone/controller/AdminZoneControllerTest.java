package com.swiftdrop.backend.modules.zone.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.pricing.controller.PricingExceptionHandler;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.zone.dto.AdminZoneResponse;
import com.swiftdrop.backend.modules.zone.service.ZoneAdminService;

@WebMvcTest(AdminZoneController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PricingExceptionHandler.class)
class AdminZoneControllerTest {
    private static final UUID ZONE_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final AdminZoneResponse ZONE =
            new AdminZoneResponse(ZONE_ID, "NORTH_1", "North Zone", new BigDecimal("75.00"), true, null);

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ZoneAdminService zoneAdminService;
    /** The auth module's JWT filter is part of every web test slice and needs this bean. */
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test void listsAllZones() throws Exception {
        when(zoneAdminService.listAll()).thenReturn(List.of(ZONE));
        mockMvc.perform(get("/api/v1/admin/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NORTH_1"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test void createsZone() throws Exception {
        when(zoneAdminService.create(any())).thenReturn(ZONE);
        mockMvc.perform(post("/api/v1/admin/zones").contentType("application/json")
                        .content("{\"code\":\"NORTH_1\",\"name\":\"North Zone\",\"surcharge\":75}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ZONE_ID.toString()))
                .andExpect(jsonPath("$.surcharge").value(75.00));
    }

    @Test void negativeSurchargeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/zones").contentType("application/json")
                        .content("{\"code\":\"NORTH_1\",\"name\":\"North Zone\",\"surcharge\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verify(zoneAdminService, never()).create(any());
    }

    @Test void invalidCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/zones").contentType("application/json")
                        .content("{\"code\":\"north zone!\",\"name\":\"North Zone\",\"surcharge\":0}"))
                .andExpect(status().isBadRequest());
        verify(zoneAdminService, never()).create(any());
    }

    @Test void duplicateCodeIsConflict() throws Exception {
        when(zoneAdminService.create(any())).thenThrow(PricingException.duplicateZoneCode("NORTH_1"));
        mockMvc.perform(post("/api/v1/admin/zones").contentType("application/json")
                        .content("{\"code\":\"NORTH_1\",\"name\":\"North Zone\",\"surcharge\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Zone code already used"));
    }

    @Test void updatesZone() throws Exception {
        when(zoneAdminService.update(eq(ZONE_ID), any())).thenReturn(ZONE);
        mockMvc.perform(put("/api/v1/admin/zones/" + ZONE_ID).contentType("application/json")
                        .content("{\"name\":\"North Zone\",\"surcharge\":75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("North Zone"));
    }

    @Test void unknownZoneIsNotFound() throws Exception {
        when(zoneAdminService.update(eq(ZONE_ID), any())).thenThrow(PricingException.zoneNotFound(ZONE_ID));
        mockMvc.perform(put("/api/v1/admin/zones/" + ZONE_ID).contentType("application/json")
                        .content("{\"name\":\"North Zone\",\"surcharge\":75}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Zone not found"));
    }

    @Test void malformedZoneIdIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/admin/zones/not-a-uuid").contentType("application/json")
                        .content("{\"name\":\"North Zone\",\"surcharge\":75}"))
                .andExpect(status().isBadRequest());
    }

    @Test void togglesActive() throws Exception {
        var inactive = new AdminZoneResponse(ZONE_ID, "NORTH_1", "North Zone", BigDecimal.ZERO, false, null);
        when(zoneAdminService.setActive(ZONE_ID, false)).thenReturn(inactive);
        mockMvc.perform(patch("/api/v1/admin/zones/" + ZONE_ID + "/active").contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test void deactivatingLastActiveZoneIsConflict() throws Exception {
        when(zoneAdminService.setActive(ZONE_ID, false)).thenThrow(PricingException.lastActiveZone());
        mockMvc.perform(patch("/api/v1/admin/zones/" + ZONE_ID + "/active").contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("At least one zone must stay active"));
    }

    @Test void missingActiveFlagIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/zones/" + ZONE_ID + "/active").contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
