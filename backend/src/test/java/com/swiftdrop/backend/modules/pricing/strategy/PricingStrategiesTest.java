package com.swiftdrop.backend.modules.pricing.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

    @Test void standardCalculatesSameZoneAndWeightEdge() {
        var result = new StandardPricingStrategy().calculate(context(ServiceType.STANDARD, "590", "100", "250", ZONE_A, ZONE_A, "0.5"));
        assertMoney("50.00", result.weightCharge());
        assertMoney("0.00", result.zoneCharge());
        assertMoney("640.00", result.totalFee());
    }

    @Test void expressCalculatesCrossZoneAndWeightEdge() {
        var result = new ExpressPricingStrategy().calculate(context(ServiceType.EXPRESS, "990", "100", "400", ZONE_A, ZONE_B, "2"));
        assertMoney("200.00", result.weightCharge());
        assertMoney("400.00", result.zoneCharge());
        assertMoney("1590.00", result.totalFee());
    }

    @Test void sameDayAllowsSameZoneAndRejectsCrossZone() {
        var strategy = new SameDayPricingStrategy();
        var sameZone = strategy.calculate(context(ServiceType.SAME_DAY, "1490", "100", "600", ZONE_A, ZONE_A, "30"));
        assertMoney("4490.00", sameZone.totalFee());
        assertThrows(PricingException.class,
                () -> strategy.calculate(context(ServiceType.SAME_DAY, "1490", "100", "600", ZONE_A, ZONE_B, "1")));
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
        return new PricingContext(rule, pickup, dropoff, new BigDecimal(weight));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
