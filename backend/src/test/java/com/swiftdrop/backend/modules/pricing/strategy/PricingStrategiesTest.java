package com.swiftdrop.backend.modules.pricing.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.PricingContext;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.zone.entity.Zone;

class PricingStrategiesTest {
    private static final UUID ZONE_A = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID ZONE_B = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 11);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);

    @Test void firstKilogramIsIncludedInBaseFee() {
        var strategy = new StandardPricingStrategy();
        var documents = strategy.calculate(context(ServiceType.STANDARD, "590", "100", "250", ZONE_A, ZONE_A, "0.5"));
        assertMoney("0.00", documents.weightCharge());
        assertMoney("590.00", documents.totalFee());

        var oneKilogram = strategy.calculate(context(ServiceType.STANDARD, "590", "100", "250", ZONE_A, ZONE_A, "1"));
        assertMoney("590.00", oneKilogram.baseFee());
        assertMoney("0.00", oneKilogram.weightCharge());
        assertMoney("0.00", oneKilogram.zoneCharge());
        assertMoney("590.00", oneKilogram.totalFee());
    }

    @Test void standardChargesOnlyWeightAboveFirstKilogram() {
        var result = new StandardPricingStrategy().calculate(context(ServiceType.STANDARD, "590", "100", "250", ZONE_A, ZONE_A, "1.5"));
        assertMoney("50.00", result.weightCharge());
        assertMoney("640.00", result.totalFee());
    }

    @Test void expressCalculatesCrossZoneAndWeightEdge() {
        var result = new ExpressPricingStrategy().calculate(context(ServiceType.EXPRESS, "990", "100", "400", ZONE_A, ZONE_B, "2"));
        assertMoney("100.00", result.weightCharge());
        assertMoney("400.00", result.zoneCharge());
        assertMoney("1490.00", result.totalFee());
    }

    @Test void sameDayAllowsSameZoneAndRejectsCrossZone() {
        var strategy = new SameDayPricingStrategy();
        var sameZone = strategy.calculate(context(ServiceType.SAME_DAY, "1490", "100", "600", ZONE_A, ZONE_A, "30"));
        assertMoney("2900.00", sameZone.weightCharge());
        assertMoney("4390.00", sameZone.totalFee());
        assertThrows(PricingException.class,
                () -> strategy.calculate(context(ServiceType.SAME_DAY, "1490", "100", "600", ZONE_A, ZONE_B, "1")));
    }

    @Test void standardDeliversInThreeWorkingDays() {
        var strategy = new StandardPricingStrategy();
        assertEquals(LocalDate.of(2026, 11, 16), strategy.estimateDeliveryDate(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 11, 18), strategy.estimateDeliveryDate(FRIDAY));
    }

    @Test void expressDeliversNextWorkingDay() {
        var strategy = new ExpressPricingStrategy();
        assertEquals(LocalDate.of(2026, 11, 12), strategy.estimateDeliveryDate(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 11, 16), strategy.estimateDeliveryDate(FRIDAY));
    }

    @Test void sameDayDeliversToday() {
        assertEquals(FRIDAY, new SameDayPricingStrategy().estimateDeliveryDate(FRIDAY));
    }

    @Test void breakdownIncludesEndOfDeliveryDayInSriLankaTime() {
        var result = new StandardPricingStrategy().calculate(context(ServiceType.STANDARD, "590", "100", "250", ZONE_A, ZONE_A, "1"));
        // Booked Friday 13 Nov -> Wednesday 18 Nov, 18:00 Asia/Colombo (UTC+05:30).
        assertEquals(Instant.parse("2026-11-18T12:30:00Z"), result.estimatedDeliveryAt());
    }

    private static PricingContext context(ServiceType type, String base, String perKg, String interZone,
                                          UUID pickupId, UUID dropoffId, String weight) {
        var rule = mock(PricingRule.class);
        when(rule.getServiceType()).thenReturn(type);
        when(rule.getBaseFee()).thenReturn(new BigDecimal(base));
        when(rule.getPerKgFee()).thenReturn(new BigDecimal(perKg));
        when(rule.getInterZoneFee()).thenReturn(new BigDecimal(interZone));
        var pickup = mock(Zone.class);
        when(pickup.getId()).thenReturn(pickupId);
        when(pickup.getSurcharge()).thenReturn(BigDecimal.ZERO);
        var dropoff = mock(Zone.class);
        when(dropoff.getId()).thenReturn(dropoffId);
        when(dropoff.getSurcharge()).thenReturn(BigDecimal.ZERO);
        return new PricingContext(rule, pickup, dropoff, new BigDecimal(weight), FRIDAY);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
