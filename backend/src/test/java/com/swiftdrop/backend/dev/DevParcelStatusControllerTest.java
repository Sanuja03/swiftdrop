package com.swiftdrop.backend.dev;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.common.security.DefaultSecurityConfig;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.tracking.controller.TrackingExceptionHandler;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.support.TestParcels;

@ActiveProfiles(DevProfile.NAME)
@WebMvcTest(DevParcelStatusController.class)
@Import({DevSecurityConfig.class, DefaultSecurityConfig.class, TrackingExceptionHandler.class})
class DevParcelStatusControllerTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.swiftdrop.backend.modules.auth.security.JwtTokenProvider jwtTokenProvider;

    private static final UUID PARCEL_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParcelLookupService parcelLookupService;
    @MockitoBean
    private ParcelStatusService parcelStatusService;

    @Test
    void changesStatusThroughUpdateStatus() throws Exception {
        when(parcelLookupService.getByTrackingNumber("SDP7845621")).thenReturn(TestParcels.withId(PARCEL_ID));

        mockMvc.perform(post("/api/v1/dev/parcels/SDP7845621/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\",\"note\":\"Collected\"}"))
                .andExpect(status().isNoContent());

        verify(parcelStatusService).updateStatus(PARCEL_ID, ParcelStatus.PICKED_UP, "Collected", null);
    }

    @Test
    void invalidTransitionFromAnyControllerReturns409() throws Exception {
        when(parcelLookupService.getByTrackingNumber("SDP7845621")).thenReturn(TestParcels.withId(PARCEL_ID));
        doThrow(new InvalidStatusTransitionException(ParcelStatus.CREATED, ParcelStatus.DELIVERED))
                .when(parcelStatusService).updateStatus(any(), any(), any(), isNull());

        mockMvc.perform(post("/api/v1/dev/parcels/SDP7845621/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid status change"));
    }
}
