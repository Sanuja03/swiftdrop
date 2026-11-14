package com.swiftdrop.backend.modules.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.UpdatePricingRuleRequest;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.pricing.strategy.ExpressPricingStrategy;
import com.swiftdrop.backend.modules.pricing.strategy.SameDayPricingStrategy;
import com.swiftdrop.backend.modules.pricing.strategy.StandardPricingStrategy;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

class PricingRuleAdminServiceTest {
    private final PricingRuleRepository repository = mock(PricingRuleRepository.class);
    private final PricingRuleAdminService service = new PricingRuleAdminService(repository);

    @BeforeEach
    void saveReturnsTheEntity() {
        when(repository.saveAndFlush(any(PricingRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsEveryRuleInServiceOrder() {
        when(repository.findAll()).thenReturn(List.of(
                rule(ServiceType.SAME_DAY, "1490", true), rule(ServiceType.STANDARD, "590", true),
                rule(ServiceType.EXPRESS, "990", false)));

        var rules = service.listAll();

        assertThat(rules).extracting(r -> r.serviceType())
                .containsExactly(ServiceType.STANDARD, ServiceType.EXPRESS, ServiceType.SAME_DAY);
        assertThat(rules).extracting(r -> r.active()).containsExactly(true, false, true);
        assertThat(rules).allMatch(r -> r.currency().equals("LKR"));
    }

    @Test
    void updatesFeesAndActiveFlag() {
        var standard = rule(ServiceType.STANDARD, "590", true);
        when(repository.findByServiceType(ServiceType.STANDARD)).thenReturn(Optional.of(standard));

        var updated = service.update(ServiceType.STANDARD, request("650", "120", "300", true));

        assertThat(updated.baseFee()).isEqualByComparingTo("650");
        assertThat(updated.perKgFee()).isEqualByComparingTo("120");
        assertThat(updated.interZoneFee()).isEqualByComparingTo("300");
        assertThat(updated.active()).isTrue();
    }

    @Test
    void serviceCanBeDeactivatedWhileAnotherStaysActive() {
        var express = rule(ServiceType.EXPRESS, "990", true);
        when(repository.findByServiceType(ServiceType.EXPRESS)).thenReturn(Optional.of(express));
        when(repository.countByActiveTrue()).thenReturn(2L);

        assertThat(service.update(ServiceType.EXPRESS, request("990", "100", "400", false)).active()).isFalse();
    }

    @Test
    void lastActiveServiceCannotBeDeactivated() {
        var standard = rule(ServiceType.STANDARD, "590", true);
        when(repository.findByServiceType(ServiceType.STANDARD)).thenReturn(Optional.of(standard));
        when(repository.countByActiveTrue()).thenReturn(1L);

        assertThatThrownBy(() -> service.update(ServiceType.STANDARD, request("590", "100", "250", false)))
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(standard.isActive()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void lastActiveServiceCanStillHaveItsFeesChanged() {
        var standard = rule(ServiceType.STANDARD, "590", true);
        when(repository.findByServiceType(ServiceType.STANDARD)).thenReturn(Optional.of(standard));
        when(repository.countByActiveTrue()).thenReturn(1L);

        assertThat(service.update(ServiceType.STANDARD, request("600", "100", "250", true)).baseFee())
                .isEqualByComparingTo("600");
    }

    @Test
    void inactiveServiceCanBeReactivated() {
        var sameDay = rule(ServiceType.SAME_DAY, "1490", false);
        when(repository.findByServiceType(ServiceType.SAME_DAY)).thenReturn(Optional.of(sameDay));

        assertThat(service.update(ServiceType.SAME_DAY, request("1490", "100", "600", true)).active()).isTrue();
    }

    @Test
    void missingRuleIsNotFound() {
        when(repository.findByServiceType(ServiceType.EXPRESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ServiceType.EXPRESS, request("990", "100", "400", true)))
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** PricingService reads the rule on every call, so the next estimate already uses the saved fees. */
    @Test
    void savedChangeIsUsedByTheNextEstimate() {
        var standard = rule(ServiceType.STANDARD, "590", true);
        var zone = Zone.create("ZONE_A", "Zone A", BigDecimal.ZERO);
        var zones = mock(ZoneRepository.class);
        when(zones.findByIdAndActiveTrue(zone.getId())).thenReturn(Optional.of(zone));
        when(repository.findByServiceType(ServiceType.STANDARD)).thenReturn(Optional.of(standard));
        var pricing = new PricingService(repository, zones,
                List.of(new StandardPricingStrategy(), new ExpressPricingStrategy(), new SameDayPricingStrategy()),
                Clock.fixed(Instant.parse("2026-11-13T04:00:00Z"), ZoneOffset.UTC));
        var estimate = new EstimateRequest(zone.getId(), zone.getId(), ParcelSize.SMALL, BigDecimal.ONE, ServiceType.STANDARD);

        assertThat(pricing.estimate(estimate).totalFee()).isEqualByComparingTo("590.00");
        service.update(ServiceType.STANDARD, request("700", "100", "250", true));
        assertThat(pricing.estimate(estimate).totalFee()).isEqualByComparingTo("700.00");
    }

    private static UpdatePricingRuleRequest request(String base, String perKg, String interZone, boolean active) {
        return new UpdatePricingRuleRequest(new BigDecimal(base), new BigDecimal(perKg), new BigDecimal(interZone), active);
    }

    private static PricingRule rule(ServiceType type, String baseFee, boolean active) {
        var rule = new PricingRule();
        ReflectionTestUtils.setField(rule, "serviceType", type);
        rule.update(new BigDecimal(baseFee), new BigDecimal("100"), new BigDecimal("250"), active);
        return rule;
    }
}
