package com.swiftdrop.backend.modules.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.swiftdrop.backend.modules.merchant.dto.FeeCalculationRequest;
import com.swiftdrop.backend.modules.merchant.exception.InvalidOrderException;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.pricing.dto.EstimateRequest;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.service.PricingService;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

/** Merchant fees are never computed here: they come from the shared PricingService. */
class MerchantPricingServiceTest {
    private static final UUID PICKUP_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID DROPOFF_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final FeeBreakdown FEE = new FeeBreakdown(new BigDecimal("590.00"), new BigDecimal("150.00"),
            new BigDecimal("250.00"), new BigDecimal("990.00"), "LKR", Instant.parse("2026-11-20T12:30:00Z"));

    private final PricingService pricingService = mock(PricingService.class);
    private final ZoneRepository zoneRepository = mock(ZoneRepository.class);
    private final MerchantPricingService service = new MerchantPricingService(pricingService, zoneRepository);

    @BeforeEach
    void zones() {
        var pickup = zone(PICKUP_ID, "Zone A");
        var dropoff = zone(DROPOFF_ID, "Zone B");
        when(zoneRepository.findFirstByNameIgnoreCaseAndActiveTrue(any())).thenReturn(Optional.empty());
        when(zoneRepository.findFirstByNameIgnoreCaseAndActiveTrue("zone a")).thenReturn(Optional.of(pickup));
        when(zoneRepository.findFirstByCodeIgnoreCaseAndActiveTrue(any())).thenReturn(Optional.empty());
        when(zoneRepository.findFirstByCodeIgnoreCaseAndActiveTrue("ZONE_B")).thenReturn(Optional.of(dropoff));
    }

    @Test
    void pricesThroughTheSharedPricingServiceWithResolvedZones() {
        when(pricingService.estimate(any())).thenReturn(FEE);

        var quote = service.quote(ServiceType.STANDARD, new BigDecimal("2.5"), " zone a ", "ZONE_B");

        var request = ArgumentCaptor.forClass(EstimateRequest.class);
        verify(pricingService).estimate(request.capture());
        assertThat(request.getValue().pickupZoneId()).isEqualTo(PICKUP_ID);
        assertThat(request.getValue().dropoffZoneId()).isEqualTo(DROPOFF_ID);
        assertThat(request.getValue().parcelSize()).isEqualTo(ParcelSize.MEDIUM);
        assertThat(quote.fee()).isSameAs(FEE);
    }

    @Test
    void feeResponseCopiesTheSharedBreakdown() {
        when(pricingService.estimate(any())).thenReturn(FEE);

        var response = service.calculateFee(FeeCalculationRequest.builder()
                .serviceType(ServiceType.STANDARD).weightKg(new BigDecimal("2.5"))
                .pickupCity("zone a").dropoffCity("ZONE_B").build());

        assertThat(response.getTotalFee()).isEqualByComparingTo("990.00");
        assertThat(response.getZoneSurcharge()).isEqualByComparingTo("250.00");
        assertThat(response.getCurrency()).isEqualTo("LKR");
    }

    @Test
    void picksTheSmallestParcelSizeThatFits() {
        assertThat(MerchantPricingService.sizeFor(new BigDecimal("0.4"))).isEqualTo(ParcelSize.SMALL);
        assertThat(MerchantPricingService.sizeFor(new BigDecimal("2"))).isEqualTo(ParcelSize.SMALL);
        assertThat(MerchantPricingService.sizeFor(new BigDecimal("2.01"))).isEqualTo(ParcelSize.MEDIUM);
        assertThat(MerchantPricingService.sizeFor(new BigDecimal("30"))).isEqualTo(ParcelSize.LARGE);
        assertThatThrownBy(() -> MerchantPricingService.sizeFor(new BigDecimal("30.5")))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void unknownCityIsAnInvalidOrder() {
        assertThatThrownBy(() -> service.quote(ServiceType.STANDARD, BigDecimal.ONE, "Nowhere", "ZONE_B"))
                .isInstanceOf(InvalidOrderException.class);
        verify(pricingService, never()).estimate(any());
    }

    @Test
    void pricingRuleErrorsBecomeInvalidOrders() {
        when(pricingService.estimate(any())).thenThrow(PricingException.sameDayAcrossZones());

        assertThatThrownBy(() -> service.quote(ServiceType.SAME_DAY, BigDecimal.ONE, "zone a", "ZONE_B"))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Same Day");
    }

    private static Zone zone(UUID id, String name) {
        var zone = mock(Zone.class);
        when(zone.getId()).thenReturn(id);
        when(zone.getName()).thenReturn(name);
        return zone;
    }
}
