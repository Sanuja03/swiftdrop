package com.swiftdrop.backend.modules.booking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.booking.dto.CreateParcelRequest;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelResponse;
import com.swiftdrop.backend.modules.booking.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateParcelResponse createParcel(@Valid @RequestBody CreateParcelRequest request) {
        return bookingService.createParcel(request);
    }
}
