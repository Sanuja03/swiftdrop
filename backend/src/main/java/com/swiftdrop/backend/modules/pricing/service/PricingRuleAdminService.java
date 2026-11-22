package com.swiftdrop.backend.modules.pricing.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.AdminPricingRuleResponse;
import com.swiftdrop.backend.modules.pricing.dto.UpdatePricingRuleRequest;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Admin management of the per-service pricing rules. PricingService reads the rules on every
 * estimate and price-list request (nothing is cached), so a saved change applies immediately.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PricingRuleAdminService {
    private final PricingRuleRepository pricingRuleRepository;

    @Transactional(readOnly = true)
    public List<AdminPricingRuleResponse> listAll() {
        return pricingRuleRepository.findAll().stream()
                .sorted(Comparator.comparing(PricingRule::getServiceType))
                .map(AdminPricingRuleResponse::from)
                .toList();
    }

    /**
     * @throws PricingException 404 if the service has no rule, 409 if this would deactivate the last active service
     */
    public AdminPricingRuleResponse update(ServiceType serviceType, UpdatePricingRuleRequest request) {
        PricingRule rule = pricingRuleRepository.findByServiceType(serviceType)
                .orElseThrow(() -> PricingException.ruleNotFound(serviceType));
        boolean deactivating = rule.isActive() && !request.active();
        if (deactivating && pricingRuleRepository.countByActiveTrue() <= 1) {
            throw PricingException.lastActiveService();
        }
        rule.update(request.baseFee(), request.perKgFee(), request.interZoneFee(), request.active());
        return AdminPricingRuleResponse.from(pricingRuleRepository.saveAndFlush(rule));
    }
}
