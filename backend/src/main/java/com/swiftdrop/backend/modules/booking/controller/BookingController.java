package com.swiftdrop.backend.modules.booking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.booking.dto.BookingDetailsResponse;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelRequest;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelResponse;
import com.swiftdrop.backend.modules.booking.service.BookingService;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/** Customer bookings. Both endpoints act for the signed-in customer only (ROLE_CUSTOMER). */
@RestController
@RequestMapping(BookingController.BASE_PATH)
@RequiredArgsConstructor
public class BookingController {
    public static final String BASE_PATH = "/api/v1/parcels";

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateParcelResponse createParcel(@Valid @RequestBody CreateParcelRequest request) {
        return bookingService.createParcel(request);
    }

    @GetMapping("/{trackingNumber}")
    public BookingDetailsResponse getBooking(
            @PathVariable @Pattern(regexp = ParcelLookupService.TRACKING_NUMBER_PATTERN) String trackingNumber) {
        return bookingService.getBooking(trackingNumber);
    }
}
