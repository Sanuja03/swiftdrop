package com.swiftdrop.backend.modules.auth.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.common.security.CurrentCustomer;
import com.swiftdrop.backend.common.security.CurrentCustomerProvider;
import com.swiftdrop.backend.modules.booking.controller.BookingController;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelResponse;
import com.swiftdrop.backend.modules.booking.service.BookingService;
import com.swiftdrop.backend.modules.pricing.controller.PricingController;
import com.swiftdrop.backend.modules.pricing.controller.ServicePriceController;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.service.PricingService;
import com.swiftdrop.backend.modules.tracking.controller.CustomerShipmentController;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.CustomerShipmentQueryService;
import com.swiftdrop.backend.modules.zone.controller.ZoneController;
import com.swiftdrop.backend.modules.zone.service.ZoneService;

/**
 * URL rules of the main security chain for the booking, pricing and customer APIs:
 * public catalogue and estimate, customer-only bookings and shipment lists, admin-only admin APIs.
 */
@WebMvcTest(controllers = {BookingController.class, PricingController.class, ServicePriceController.class,
        ZoneController.class, CustomerShipmentController.class})
@Import({SecurityConfig.class, CustomAccessDeniedHandler.class, CustomAuthenticationEntryPoint.class,
        JwtAuthenticationFilter.class})
class ApiAccessSecurityTest {
    private static final UUID ZONE_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private BookingService bookingService;
    @MockitoBean private PricingService pricingService;
    @MockitoBean private ZoneService zoneService;
    @MockitoBean private CustomerShipmentQueryService customerShipmentQueryService;
    @MockitoBean private CurrentCustomerProvider currentCustomerProvider;

    @Test
    void zonesAndPricesArePublic() throws Exception {
        when(zoneService.getActiveZones()).thenReturn(List.of());
        when(pricingService.servicePrices()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/zones")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pricing/services")).andExpect(status().isOk());
    }

    @Test
    void estimateIsPublic() throws Exception {
        when(pricingService.estimate(any())).thenReturn(new FeeBreakdown(new BigDecimal("590.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("590.00"), "LKR", Instant.parse("2026-11-18T12:30:00Z")));

        mockMvc.perform(post("/api/v1/parcels/estimate").contentType("application/json").content(estimateBody()))
                .andExpect(status().isOk());
    }

    @Test
    void bookingNeedsSignIn() throws Exception {
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json").content(bookingBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/parcels/SDP0000001")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "COURIER")
    void nonCustomersCannotBook() throws Exception {
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json").content(bookingBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customersCanBook() throws Exception {
        when(bookingService.createParcel(any())).thenReturn(new CreateParcelResponse(UUID.randomUUID(), "SDP0000001",
                ParcelStatus.CREATED, new BigDecimal("590.00"), "LKR", Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/parcels").contentType("application/json").content(bookingBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void customerShipmentsNeedSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/customer/parcels")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customersCanListTheirShipments() throws Exception {
        when(currentCustomerProvider.currentCustomer()).thenReturn(new CurrentCustomer(UUID.randomUUID(), "Test Customer"));
        when(customerShipmentQueryService.getShipments(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customer/parcels")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void adminApisAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/zones")).andExpect(status().isForbidden());
    }

    private static String estimateBody() {
        return """
                {"pickupZoneId":"%s","dropoffZoneId":"%s","parcelSize":"SMALL","weightKg":1,"serviceType":"STANDARD"}
                """.formatted(ZONE_ID, ZONE_ID);
    }

    private static String bookingBody() {
        return """
                {"pickupZoneId":"%s","dropoffZoneId":"%s","parcelSize":"SMALL","weightKg":1,"serviceType":"STANDARD",
                 "pickupAddress":"1 Sample Street","dropoffAddress":"2 Example Avenue",
                 "recipientName":"Test Recipient","recipientPhone":"0700000000"}
                """.formatted(ZONE_ID, ZONE_ID);
    }
}
