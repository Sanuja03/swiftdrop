package com.swiftdrop.backend.modules.tracking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.tracking.dto.PublicTrackingResponse;
import com.swiftdrop.backend.modules.tracking.service.TrackingQueryService;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * Public tracking API: no login needed, only a tracking number.
 */
@RestController
@RequestMapping(PublicTrackingController.BASE_PATH)
@RequiredArgsConstructor
public class PublicTrackingController {

    public static final String BASE_PATH = "/api/v1/tracking/public";
    public static final String TRACKING_NUMBER_PATTERN = "^[A-Za-z0-9-]{6,32}$";

    private final TrackingQueryService trackingQueryService;

    @GetMapping("/{trackingNumber}")
    public PublicTrackingResponse track(
            @PathVariable @Pattern(regexp = TRACKING_NUMBER_PATTERN, message = "is not a valid tracking number")
            String trackingNumber) {
        return trackingQueryService.getPublicTracking(trackingNumber);
    }
}
