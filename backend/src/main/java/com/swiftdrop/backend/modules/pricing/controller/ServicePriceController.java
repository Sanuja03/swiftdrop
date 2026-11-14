package com.swiftdrop.backend.modules.pricing.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.pricing.dto.ServicePriceResponse;
import com.swiftdrop.backend.modules.pricing.service.PricingService;

import lombok.RequiredArgsConstructor;

/** Public price list: the starting price of each active delivery service. */
@RestController
@RequestMapping(ServicePriceController.BASE_PATH)
@RequiredArgsConstructor
public class ServicePriceController {
    public static final String BASE_PATH = "/api/v1/pricing";

    private final PricingService pricingService;

    @GetMapping("/services")
    public List<ServicePriceResponse> servicePrices() {
        return pricingService.servicePrices();
    }
}
