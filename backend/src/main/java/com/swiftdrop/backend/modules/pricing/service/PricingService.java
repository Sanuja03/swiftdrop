package com.swiftdrop.backend.modules.pricing.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.pricing.strategy.PricingStrategy;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

@Service
@Transactional(readOnly = true)
public class PricingService {
    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneRepository zoneRepository;
    private final Map<ServiceType, PricingStrategy> strategies;

    public PricingService(PricingRuleRepository pricingRuleRepository, ZoneRepository zoneRepository,
                          List<PricingStrategy> pricingStrategies) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.zoneRepository = zoneRepository;
        this.strategies = pricingStrategies.stream().collect(Collectors.toUnmodifiableMap(
                PricingStrategy::supports, Function.identity()));
    }

    public FeeBreakdown estimate(EstimateRequest request) {
        validateWeight(request);
        var pickupZone = zoneRepository.findByIdAndActiveTrue(request.pickupZoneId())
                .orElseThrow(() -> PricingException.unknownZone(request.pickupZoneId()));
        var dropoffZone = zoneRepository.findByIdAndActiveTrue(request.dropoffZoneId())
                .orElseThrow(() -> PricingException.unknownZone(request.dropoffZoneId()));
        var rule = pricingRuleRepository.findByServiceType(request.serviceType())
                .filter(pricingRule -> pricingRule.isActive())
                .orElseThrow(() -> PricingException.inactiveRule(request.serviceType()));
        var strategy = strategies.get(request.serviceType());
        if (strategy == null) throw PricingException.inactiveRule(request.serviceType());
        return strategy.calculate(new PricingContext(rule, pickupZone, dropoffZone, request.weightKg()));
    }

    private static void validateWeight(EstimateRequest request) {
        BigDecimal weight = request.weightKg();
        var size = request.parcelSize();
        if (weight == null || size == null || weight.signum() <= 0
                || weight.compareTo(size.minWeight()) < 0 || weight.compareTo(size.maxWeight()) > 0) {
            throw new IllegalArgumentException("Weight is outside the selected parcel size range.");
        }
    }
}
