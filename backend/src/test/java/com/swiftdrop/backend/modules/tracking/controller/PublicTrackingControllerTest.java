package com.swiftdrop.backend.modules.tracking.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.common.security.DefaultSecurityConfig;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.tracking.config.TrackingSecurityConfig;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.dto.PublicTrackingResponse;
import com.swiftdrop.backend.modules.tracking.dto.TrackingEventResponse;
import com.swiftdrop.backend.modules.tracking.service.TrackingQueryService;

@WebMvcTest(PublicTrackingController.class)
@Import({TrackingSecurityConfig.class, DefaultSecurityConfig.class})
class PublicTrackingControllerTest {

    /** The auth module's JWT filter is part of every web test slice and needs this bean. */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingQueryService trackingQueryService;

    @Test
    void returnsTrackingWithoutLogin() throws Exception {
        Instant created = Instant.parse("2026-11-05T08:00:00Z");
        Instant pickedUp = Instant.parse("2026-11-05T09:30:00Z");
        when(trackingQueryService.getPublicTracking("SDP7845621")).thenReturn(new PublicTrackingResponse(
                "SDP7845621", ParcelStatus.PICKED_UP, "Picked Up", "Colombo", "Dehiwala",
                ServiceType.STANDARD, ServiceType.STANDARD.getLabel(), null, pickedUp,
                List.of(new TrackingEventResponse(ParcelStatus.CREATED, "Order Created", created),
                        new TrackingEventResponse(ParcelStatus.PICKED_UP, "Picked Up", pickedUp))));

        mockMvc.perform(get("/api/v1/tracking/public/SDP7845621"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SDP7845621"))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.originCity").value("Colombo"))
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[1].changedAt").value("2026-11-05T09:30:00Z"));
    }

    @Test
    void unknownTrackingNumberReturns404() throws Exception {
        when(trackingQueryService.getPublicTracking(anyString()))
                .thenThrow(new ParcelNotFoundException("SDP0000000"));

        mockMvc.perform(get("/api/v1/tracking/public/SDP0000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Parcel not found"));
    }

    @Test
    void malformedTrackingNumberReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/bad!!"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(trackingQueryService);
    }

    @Test
    void otherUrlsStillRequireLogin() throws Exception {
        mockMvc.perform(get("/api/v1/receipts/SDP7845621"))
                .andExpect(status().isUnauthorized());
    }
}
