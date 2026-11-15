package com.swiftdrop.backend.modules.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.pricing.strategy.ExpressPricingStrategy;
import com.swiftdrop.backend.modules.pricing.strategy.SameDayPricingStrategy;
import com.swiftdrop.backend.modules.pricing.strategy.StandardPricingStrategy;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

/** Starting prices come from the real strategies, so they always match an actual estimate. */
class ServicePricesTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), ZoneOffset.UTC);

    private final PricingRuleRepository rules = mock(PricingRuleRepository.class);
    private final ZoneRepository zones = mock(ZoneRepository.class);
    private final PricingService service = new PricingService(rules, zones,
            List.of(new StandardPricingStrategy(), new ExpressPricingStrategy(), new SameDayPricingStrategy()), CLOCK);

    @Test
    void startingPriceIsOneKilogramWithinTheCheapestZone() {
        var activeZones = List.of(
                zone("10000000-0000-4000-8000-000000000001", "150"),
                zone("10000000-0000-4000-8000-000000000002", "0"));
        var activeRules = List.of(
                rule(ServiceType.SAME_DAY, "1490"), rule(ServiceType.STANDARD, "590"), rule(ServiceType.EXPRESS, "990"));
        when(zones.findAllByActiveTrueOrderByNameAsc()).thenReturn(activeZones);
        when(rules.findAllByActiveTrue()).thenReturn(activeRules);

        var prices = service.servicePrices();

        assertThat(prices).extracting(price -> price.serviceType())
                .containsExactly(ServiceType.STANDARD, ServiceType.EXPRESS, ServiceType.SAME_DAY);
        assertThat(prices.get(0).fromPrice()).isEqualByComparingTo("590.00");
        assertThat(prices.get(1).fromPrice()).isEqualByComparingTo("990.00");
        assertThat(prices.get(2).fromPrice()).isEqualByComparingTo("1490.00");
        assertThat(prices).allMatch(price -> price.currency().equals("LKR"));
    }

    @Test
    void surchargeOfTheCheapestZoneIsIncluded() {
        var activeZones = List.of(zone("10000000-0000-4000-8000-000000000001", "150"));
        var activeRules = List.of(rule(ServiceType.STANDARD, "590"));
        when(zones.findAllByActiveTrueOrderByNameAsc()).thenReturn(activeZones);
        when(rules.findAllByActiveTrue()).thenReturn(activeRules);

        assertThat(service.servicePrices().getFirst().fromPrice()).isEqualByComparingTo("740.00");
    }

    @Test
    void noActiveZoneMeansNoPrices() {
        when(zones.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        assertThat(service.servicePrices()).isEmpty();
    }

    private static Zone zone(String id, String surcharge) {
        var zone = mock(Zone.class);
        when(zone.getId()).thenReturn(UUID.fromString(id));
        when(zone.getSurcharge()).thenReturn(new BigDecimal(surcharge));
        return zone;
    }

    private static PricingRule rule(ServiceType type, String baseFee) {
        var rule = mock(PricingRule.class);
        when(rule.getServiceType()).thenReturn(type);
        when(rule.isActive()).thenReturn(true);
        when(rule.getBaseFee()).thenReturn(new BigDecimal(baseFee));
        when(rule.getPerKgFee()).thenReturn(new BigDecimal("100"));
        when(rule.getInterZoneFee()).thenReturn(new BigDecimal("250"));
        return rule;
    }
}
