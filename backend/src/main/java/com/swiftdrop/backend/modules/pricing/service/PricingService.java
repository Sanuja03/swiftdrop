package com.swiftdrop.backend.modules.pricing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
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
import com.swiftdrop.backend.modules.pricing.dto.ServicePriceResponse;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.pricing.strategy.DeliveryCalendar;
import com.swiftdrop.backend.modules.pricing.strategy.PricingStrategy;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

@Service
@Transactional(readOnly = true)
public class PricingService {
    private final PricingRuleRepository pricingRuleRepository;
    private final ZoneRepository zoneRepository;
    private final Map<ServiceType, PricingStrategy> strategies;
    private final Clock clock;

    public PricingService(PricingRuleRepository pricingRuleRepository, ZoneRepository zoneRepository,
                          List<PricingStrategy> pricingStrategies, Clock clock) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.zoneRepository = zoneRepository;
        this.clock = clock;
        this.strategies = pricingStrategies.stream().collect(Collectors.toUnmodifiableMap(
                PricingStrategy::supports, Function.identity()));
    }

    /**
     * Starting price of every active service, worked out by its own strategy for the cheapest
     * possible booking: the included weight, within the active zone with the lowest surcharge.
     * Empty when no zone is active.
     */
    public List<ServicePriceResponse> servicePrices() {
        var cheapestZone = zoneRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .min(Comparator.comparing(Zone::getSurcharge));
        if (cheapestZone.isEmpty()) return List.of();
        var zone = cheapestZone.get();
        var bookedOn = LocalDate.now(clock.withZone(DeliveryCalendar.BUSINESS_ZONE));
        return pricingRuleRepository.findAllByActiveTrue().stream()
                .filter(rule -> strategies.containsKey(rule.getServiceType()))
                .sorted(Comparator.comparing(PricingRule::getServiceType))
                .map(rule -> {
                    var fee = strategies.get(rule.getServiceType()).calculate(
                            new PricingContext(rule, zone, zone, PricingStrategy.INCLUDED_WEIGHT_KG, bookedOn));
                    return new ServicePriceResponse(rule.getServiceType(), fee.totalFee(), fee.currency());
                })
                .toList();
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
        var bookedOn = LocalDate.now(clock.withZone(DeliveryCalendar.BUSINESS_ZONE));
        return strategy.calculate(new PricingContext(rule, pickupZone, dropoffZone, request.weightKg(), bookedOn));
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
