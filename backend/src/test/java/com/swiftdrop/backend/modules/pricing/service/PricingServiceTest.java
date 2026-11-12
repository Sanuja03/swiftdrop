package com.swiftdrop.backend.modules.pricing.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.pricing.strategy.PricingStrategy;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

class PricingServiceTest {
    @Test void selectsStrategyUsingSupportsKeyAndLoadsRepositories() {
        var rules = mock(PricingRuleRepository.class);
        var zones = mock(ZoneRepository.class);
        var standard = mock(PricingStrategy.class);
        var express = mock(PricingStrategy.class);
        var sameDay = mock(PricingStrategy.class);
        when(standard.supports()).thenReturn(ServiceType.STANDARD);
        when(express.supports()).thenReturn(ServiceType.EXPRESS);
        when(sameDay.supports()).thenReturn(ServiceType.SAME_DAY);
        var pickupId = UUID.fromString("30000000-0000-4000-8000-000000000011");
        var dropoffId = UUID.fromString("30000000-0000-4000-8000-000000000012");
        var pickup = mock(Zone.class);
        var dropoff = mock(Zone.class);
        var rule = mock(PricingRule.class);
        when(pickup.getId()).thenReturn(pickupId);
        when(dropoff.getId()).thenReturn(dropoffId);
        when(zones.findByIdAndActiveTrue(pickupId)).thenReturn(Optional.of(pickup));
        when(zones.findByIdAndActiveTrue(dropoffId)).thenReturn(Optional.of(dropoff));
        when(rules.findByServiceType(ServiceType.EXPRESS)).thenReturn(Optional.of(rule));
        when(rule.isActive()).thenReturn(true);
        var expected = new FeeBreakdown(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("3.00"), "LKR");
        when(express.calculate(org.mockito.ArgumentMatchers.any())).thenReturn(expected);
        var service = new PricingService(rules, zones, List.of(standard, express, sameDay));

        var actual = service.estimate(new EstimateRequest(pickupId, dropoffId,
                com.swiftdrop.backend.modules.pricing.dto.ParcelSize.SMALL, new BigDecimal("1"), ServiceType.EXPRESS));

        assertSame(expected, actual);
        verify(express).calculate(org.mockito.ArgumentMatchers.any());
    }
}
