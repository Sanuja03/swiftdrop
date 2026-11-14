package com.swiftdrop.backend.modules.pricing.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.AdminPricingRuleResponse;
import com.swiftdrop.backend.modules.pricing.dto.UpdatePricingRuleRequest;
import com.swiftdrop.backend.modules.pricing.service.PricingRuleAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Pricing rule management, one rule per service type. ROLE_ADMIN only (SecurityConfig: /api/v1/admin/**). */
@RestController
@RequestMapping(AdminPricingRuleController.BASE_PATH)
@RequiredArgsConstructor
public class AdminPricingRuleController {
    public static final String BASE_PATH = "/api/v1/admin/pricing-rules";

    private final PricingRuleAdminService pricingRuleAdminService;

    @GetMapping
    public List<AdminPricingRuleResponse> listAll() {
        return pricingRuleAdminService.listAll();
    }

    @PutMapping("/{serviceType}")
    public AdminPricingRuleResponse update(@PathVariable ServiceType serviceType,
                                           @Valid @RequestBody UpdatePricingRuleRequest request) {
        return pricingRuleAdminService.update(serviceType, request);
    }
}
