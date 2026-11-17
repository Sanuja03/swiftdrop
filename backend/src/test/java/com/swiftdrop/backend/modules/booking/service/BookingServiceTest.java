package com.swiftdrop.backend.modules.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.backend.common.security.CurrentCustomer;
import com.swiftdrop.backend.common.security.CurrentCustomerProvider;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelRequest;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.pricing.entity.PricingRule;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.repository.PricingRuleRepository;
import com.swiftdrop.backend.modules.pricing.service.PricingService;
import com.swiftdrop.backend.modules.pricing.strategy.ExpressPricingStrategy;
import com.swiftdrop.backend.modules.pricing.strategy.SameDayPricingStrategy;
import com.swiftdrop.backend.modules.pricing.strategy.StandardPricingStrategy;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

/**
 * Uses the real PricingService and strategies (over mocked repositories) so the
 * booked fee is checked against the very same calculation the estimate API uses.
 */
class BookingServiceTest {
    // Friday 13 Nov 2026, 09:30 in Sri Lanka.
    private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");
    private static final UUID PICKUP_ZONE_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID DROPOFF_ZONE_ID = UUID.fromString("40000000-0000-4000-8000-000000000002");
    private static final UUID UNKNOWN_ZONE_ID = UUID.fromString("40000000-0000-4000-8000-000000000099");
    private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID PARCEL_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final String TRACKING_NUMBER = "SDP0000001";

    private ZoneRepository zoneRepository;
    private ParcelRepository parcelRepository;
    private CurrentCustomerProvider customerProvider;
    private ParcelStatusService parcelStatusService;
    private PricingService pricingService;
    private BookingService service;

    @BeforeEach
    void setUp() {
        zoneRepository = mock(ZoneRepository.class);
        parcelRepository = mock(ParcelRepository.class);
        customerProvider = mock(CurrentCustomerProvider.class);
        parcelStatusService = mock(ParcelStatusService.class);
        var trackingNumbers = mock(TrackingNumberGenerator.class);
        var pricingRules = mock(PricingRuleRepository.class);

        var pickup = zone(PICKUP_ZONE_ID, "Pickup Zone", "0");
        var dropoff = zone(DROPOFF_ZONE_ID, "Dropoff Zone", "150");
        when(zoneRepository.findByIdAndActiveTrue(PICKUP_ZONE_ID)).thenReturn(Optional.of(pickup));
        when(zoneRepository.findByIdAndActiveTrue(DROPOFF_ZONE_ID)).thenReturn(Optional.of(dropoff));
        when(zoneRepository.findByIdAndActiveTrue(UNKNOWN_ZONE_ID)).thenReturn(Optional.empty());
        var standardRule = rule("590", "100", "250");
        var sameDayRule = rule("1490", "100", "600");
        when(pricingRules.findByServiceType(ServiceType.STANDARD)).thenReturn(Optional.of(standardRule));
        when(pricingRules.findByServiceType(ServiceType.SAME_DAY)).thenReturn(Optional.of(sameDayRule));
        when(trackingNumbers.next()).thenReturn(TRACKING_NUMBER);
        when(customerProvider.currentCustomer()).thenReturn(new CurrentCustomer(CUSTOMER_ID, "Test Customer"));
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> {
            Parcel parcel = invocation.getArgument(0);
            ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
            ReflectionTestUtils.setField(parcel, "createdAt", NOW);
            return parcel;
        });

        pricingService = new PricingService(pricingRules, zoneRepository,
                List.of(new StandardPricingStrategy(), new ExpressPricingStrategy(), new SameDayPricingStrategy()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new BookingService(zoneRepository, pricingService, parcelRepository, trackingNumbers,
                customerProvider, parcelStatusService, new ParcelLookupService(parcelRepository));
    }

    @Test
    void createsParcelWithZoneCitiesFeeBreakdownAndInitialStatus() {
        var response = service.createParcel(request(PICKUP_ZONE_ID, DROPOFF_ZONE_ID, ServiceType.STANDARD));

        var saved = ArgumentCaptor.forClass(Parcel.class);
        verify(parcelRepository).save(saved.capture());
        Parcel parcel = saved.getValue();
        assertThat(parcel.getTrackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(parcel.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(parcel.getSenderName()).isEqualTo("Test Customer");
        assertThat(parcel.getRecipientName()).isEqualTo("Test Recipient");
        assertThat(parcel.getRecipientPhone()).isEqualTo("0700000000");
        assertThat(parcel.getPickupAddress()).isEqualTo("1 Sample Street");
        assertThat(parcel.getPickupCity()).isEqualTo("Pickup Zone");
        assertThat(parcel.getPickupZoneId()).isEqualTo(PICKUP_ZONE_ID);
        assertThat(parcel.getDropoffAddress()).isEqualTo("2 Example Avenue");
        assertThat(parcel.getDropoffCity()).isEqualTo("Dropoff Zone");
        assertThat(parcel.getDropoffZoneId()).isEqualTo(DROPOFF_ZONE_ID);
        assertThat(parcel.getParcelSize()).isEqualTo(ParcelSize.MEDIUM);
        assertThat(parcel.getServiceType()).isEqualTo(ServiceType.STANDARD);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        // 590 base + 1.5 kg x 100 + (250 inter-zone + 150 drop-off surcharge)
        assertThat(parcel.getBaseFee()).isEqualByComparingTo("590.00");
        assertThat(parcel.getWeightCharge()).isEqualByComparingTo("150.00");
        assertThat(parcel.getZoneCharge()).isEqualByComparingTo("400.00");
        assertThat(parcel.getFee()).isEqualByComparingTo("1140.00");
        // Friday booking, 3 working days -> Wednesday 18:00 Sri Lanka time
        assertThat(parcel.getEstimatedDeliveryAt()).isEqualTo(Instant.parse("2026-11-18T12:30:00Z"));

        verify(parcelStatusService).recordCreation(parcel, CUSTOMER_ID);

        assertThat(response.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(response.trackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(response.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(response.totalFee()).isEqualByComparingTo("1140.00");
        assertThat(response.currency()).isEqualTo("LKR");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-11-18T12:30:00Z"));
    }

    @Test
    void bookedFeeEqualsEstimate() {
        var request = request(PICKUP_ZONE_ID, DROPOFF_ZONE_ID, ServiceType.STANDARD);
        var estimate = pricingService.estimate(request.toEstimateRequest());

        var response = service.createParcel(request);

        var saved = ArgumentCaptor.forClass(Parcel.class);
        verify(parcelRepository).save(saved.capture());
        assertThat(response.totalFee()).isEqualByComparingTo(estimate.totalFee());
        assertThat(response.currency()).isEqualTo(estimate.currency());
        assertThat(response.estimatedDeliveryAt()).isEqualTo(estimate.estimatedDeliveryAt());
        assertThat(saved.getValue().getFee()).isEqualByComparingTo(estimate.totalFee());
        assertThat(saved.getValue().getBaseFee()).isEqualByComparingTo(estimate.baseFee());
        assertThat(saved.getValue().getWeightCharge()).isEqualByComparingTo(estimate.weightCharge());
        assertThat(saved.getValue().getZoneCharge()).isEqualByComparingTo(estimate.zoneCharge());
    }

    @Test
    void unknownZoneIsRejectedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service.createParcel(request(PICKUP_ZONE_ID, UNKNOWN_ZONE_ID, ServiceType.STANDARD)))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining(UNKNOWN_ZONE_ID.toString());

        verify(parcelRepository, never()).save(any());
        verifyNoInteractions(customerProvider, parcelStatusService);
    }

    @Test
    void sameDayAcrossZonesIsRejectedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service.createParcel(request(PICKUP_ZONE_ID, DROPOFF_ZONE_ID, ServiceType.SAME_DAY)))
                .isInstanceOf(PricingException.class);

        verify(parcelRepository, never()).save(any());
        verifyNoInteractions(parcelStatusService);
    }

    @Test
    void ownBookingIsReturnedWithItsFeeBreakdown() {
        when(parcelRepository.findByTrackingNumber(TRACKING_NUMBER)).thenReturn(Optional.of(bookedParcel(CUSTOMER_ID)));

        var booking = service.getBooking(" sdp0000001 ");

        assertThat(booking.trackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(booking.pickupZoneName()).isEqualTo("Pickup Zone");
        assertThat(booking.dropoffZoneName()).isEqualTo("Dropoff Zone");
        assertThat(booking.recipientName()).isEqualTo("Test Recipient");
        assertThat(booking.totalFee()).isEqualByComparingTo("1140.00");
        assertThat(booking.weightCharge()).isEqualByComparingTo("150.00");
        assertThat(booking.currency()).isEqualTo("LKR");
    }

    @Test
    void anotherCustomersBookingIsReportedAsNotFound() {
        var otherCustomer = UUID.fromString("50000000-0000-4000-8000-000000000002");
        when(parcelRepository.findByTrackingNumber(TRACKING_NUMBER)).thenReturn(Optional.of(bookedParcel(otherCustomer)));

        assertThatThrownBy(() -> service.getBooking(TRACKING_NUMBER)).isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void unknownTrackingNumberIsReportedAsNotFound() {
        when(parcelRepository.findByTrackingNumber(TRACKING_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBooking(TRACKING_NUMBER)).isInstanceOf(ParcelNotFoundException.class);
    }

    private static Parcel bookedParcel(UUID customerId) {
        return Parcel.builder()
                .trackingNumber(TRACKING_NUMBER)
                .customerId(customerId)
                .senderName("Test Customer")
                .recipientName("Test Recipient")
                .recipientPhone("0700000000")
                .pickupAddress("1 Sample Street")
                .pickupCity("Pickup Zone")
                .pickupZoneId(PICKUP_ZONE_ID)
                .dropoffAddress("2 Example Avenue")
                .dropoffCity("Dropoff Zone")
                .dropoffZoneId(DROPOFF_ZONE_ID)
                .parcelSize(ParcelSize.MEDIUM)
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("2.50"))
                .baseFee(new BigDecimal("590.00"))
                .weightCharge(new BigDecimal("150.00"))
                .zoneCharge(new BigDecimal("400.00"))
                .fee(new BigDecimal("1140.00"))
                .build();
    }

    private static CreateParcelRequest request(UUID pickupZoneId, UUID dropoffZoneId, ServiceType serviceType) {
        return new CreateParcelRequest(pickupZoneId, dropoffZoneId, ParcelSize.MEDIUM, new BigDecimal("2.5"),
                serviceType, " 1 Sample Street ", "2 Example Avenue", " Test Recipient ", "0700000000");
    }

    private static Zone zone(UUID id, String name, String surcharge) {
        var zone = mock(Zone.class);
        when(zone.getId()).thenReturn(id);
        when(zone.getName()).thenReturn(name);
        when(zone.getSurcharge()).thenReturn(new BigDecimal(surcharge));
        return zone;
    }

    private static PricingRule rule(String base, String perKg, String interZone) {
        var rule = mock(PricingRule.class);
        when(rule.isActive()).thenReturn(true);
        when(rule.getBaseFee()).thenReturn(new BigDecimal(base));
        when(rule.getPerKgFee()).thenReturn(new BigDecimal(perKg));
        when(rule.getInterZoneFee()).thenReturn(new BigDecimal(interZone));
        return rule;
    }
}
