package com.swiftdrop.backend.modules.tracking.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.common.security.CurrentCustomer;
import com.swiftdrop.backend.common.security.CurrentCustomerProvider;
import com.swiftdrop.backend.common.security.NotSignedInException;
import com.swiftdrop.backend.common.security.NotSignedInExceptionHandler;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.dto.CustomerShipmentResponse;
import com.swiftdrop.backend.modules.tracking.service.CustomerShipmentQueryService;

@WebMvcTest(CustomerShipmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NotSignedInExceptionHandler.class)
class CustomerShipmentControllerTest {
    private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CustomerShipmentQueryService queryService;
    @MockitoBean private CurrentCustomerProvider currentCustomerProvider;
    /** The auth module's JWT filter is part of every web test slice and needs this bean. */
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void listsTheSignedInCustomersShipments() throws Exception {
        when(currentCustomerProvider.currentCustomer()).thenReturn(new CurrentCustomer(CUSTOMER_ID, "Test Customer"));
        when(queryService.getShipments(CUSTOMER_ID)).thenReturn(List.of(new CustomerShipmentResponse(
                "SDP0000001", ParcelStatus.IN_TRANSIT, ParcelStatus.IN_TRANSIT.getLabel(), "Pickup Zone",
                "Dropoff Zone", ServiceType.EXPRESS, ServiceType.EXPRESS.getLabel(),
                Instant.parse("2026-11-16T12:30:00Z"), Instant.parse("2026-11-13T04:00:00Z"),
                Instant.parse("2026-11-13T06:00:00Z"))));

        mockMvc.perform(get("/api/v1/customer/parcels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber").value("SDP0000001"))
                .andExpect(jsonPath("$[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$[0].destinationCity").value("Dropoff Zone"))
                .andExpect(jsonPath("$[0].serviceLabel").value(ServiceType.EXPRESS.getLabel()))
                .andExpect(jsonPath("$[0].lastUpdatedAt").value("2026-11-13T06:00:00Z"));
    }

    @Test
    void notSignedInIsUnauthorized() throws Exception {
        when(currentCustomerProvider.currentCustomer()).thenThrow(new NotSignedInException());

        mockMvc.perform(get("/api/v1/customer/parcels"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Not signed in"));
        verifyNoInteractions(queryService);
    }
}
