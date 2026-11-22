package com.swiftdrop.backend.modules.pricing.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.service.PricingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
public class PricingController {
    private final PricingService pricingService;

    @PostMapping("/estimate")
    public FeeBreakdown estimate(@Valid @RequestBody EstimateRequest request) {
        return pricingService.estimate(request);
    }
}
