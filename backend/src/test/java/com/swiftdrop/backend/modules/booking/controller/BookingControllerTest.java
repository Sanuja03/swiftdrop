package com.swiftdrop.backend.modules.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.backend.common.security.NotSignedInException;
import com.swiftdrop.backend.common.security.NotSignedInExceptionHandler;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.booking.dto.BookingDetailsResponse;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelResponse;
import com.swiftdrop.backend.modules.booking.service.BookingService;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.pricing.controller.PricingExceptionHandler;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.tracking.controller.TrackingExceptionHandler;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({BookingExceptionHandler.class, PricingExceptionHandler.class, TrackingExceptionHandler.class,
        NotSignedInExceptionHandler.class})
class BookingControllerTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.swiftdrop.backend.modules.auth.security.JwtTokenProvider jwtTokenProvider;
    private static final UUID PARCEL_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID ZONE_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BookingService bookingService;
    /** The auth module's JWT filter is part of every web test slice and needs this bean. */
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test void validBookingReturnsCreatedParcel() throws Exception {
        when(bookingService.createParcel(any())).thenReturn(new CreateParcelResponse(PARCEL_ID, "SDP0000001",
                ParcelStatus.CREATED, new BigDecimal("1140.00"), "LKR",
                Instant.parse("2026-11-13T04:00:00Z"), Instant.parse("2026-11-18T12:30:00Z")));

        mockMvc.perform(post("/api/v1/parcels").contentType("application/json")
                        .content(body("MEDIUM", "2.5", "Test Recipient", "0700000000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelId").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("SDP0000001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalFee").value(1140.00))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-13T04:00:00Z"))
                .andExpect(jsonPath("$.estimatedDeliveryAt").value("2026-11-18T12:30:00Z"));
    }

    @Test void blankRecipientIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json")
                        .content(body("MEDIUM", "2.5", " ", "0700000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verify(bookingService, never()).createParcel(any());
    }

    @Test void invalidPhoneIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json")
                        .content(body("MEDIUM", "2.5", "Test Recipient", "not-a-phone")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verify(bookingService, never()).createParcel(any());
    }

    @Test void weightOutsideParcelSizeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json")
                        .content(body("DOCUMENTS", "0.6", "Test Recipient", "0700000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verify(bookingService, never()).createParcel(any());
    }

    @Test void malformedJsonIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
        verify(bookingService, never()).createParcel(any());
    }

    @Test void unknownZoneReturnsNotFoundProblem() throws Exception {
        when(bookingService.createParcel(any())).thenThrow(PricingException.unknownZone(ZONE_ID));
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json")
                        .content(body("MEDIUM", "2.5", "Test Recipient", "0700000000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Zone not found"));
    }

    @Test void notSignedInReturnsUnauthorizedProblem() throws Exception {
        when(bookingService.createParcel(any())).thenThrow(new NotSignedInException());
        mockMvc.perform(post("/api/v1/parcels").contentType("application/json")
                        .content(body("MEDIUM", "2.5", "Test Recipient", "0700000000")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Not signed in"));
    }

    @Test void ownBookingIsReturnedByTrackingNumber() throws Exception {
        when(bookingService.getBooking("SDP0000001")).thenReturn(new BookingDetailsResponse(PARCEL_ID, "SDP0000001",
                ParcelStatus.CREATED, "1 Sample Street", ZONE_ID, "Pickup Zone", "2 Example Avenue", ZONE_ID,
                "Dropoff Zone", ParcelSize.MEDIUM, new BigDecimal("2.50"), ServiceType.STANDARD, "Test Recipient",
                "0700000000", new BigDecimal("590.00"), new BigDecimal("150.00"), new BigDecimal("400.00"),
                new BigDecimal("1140.00"), "LKR", Instant.parse("2026-11-13T04:00:00Z"),
                Instant.parse("2026-11-18T12:30:00Z")));

        mockMvc.perform(get("/api/v1/parcels/SDP0000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SDP0000001"))
                .andExpect(jsonPath("$.pickupZoneName").value("Pickup Zone"))
                .andExpect(jsonPath("$.recipientName").value("Test Recipient"))
                .andExpect(jsonPath("$.totalFee").value(1140.00))
                .andExpect(jsonPath("$.estimatedDeliveryAt").value("2026-11-18T12:30:00Z"));
    }

    @Test void someoneElsesBookingIsNotFound() throws Exception {
        when(bookingService.getBooking("SDP0000002")).thenThrow(new ParcelNotFoundException("SDP0000002"));
        mockMvc.perform(get("/api/v1/parcels/SDP0000002"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Parcel not found"));
    }

    @Test void invalidTrackingNumberIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/bad!"))
                .andExpect(status().isBadRequest());
        verify(bookingService, never()).getBooking(any());
    }

    private static String body(String size, String weight, String recipientName, String recipientPhone) {
        return """
                {"pickupZoneId":"%s","dropoffZoneId":"%s","parcelSize":"%s","weightKg":%s,"serviceType":"STANDARD",
                 "pickupAddress":"1 Sample Street","dropoffAddress":"2 Example Avenue",
                 "recipientName":"%s","recipientPhone":"%s"}
                """.formatted(ZONE_ID, ZONE_ID, size, weight, recipientName, recipientPhone);
    }
}
