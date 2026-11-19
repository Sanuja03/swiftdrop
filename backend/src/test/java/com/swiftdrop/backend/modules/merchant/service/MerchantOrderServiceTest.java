package com.swiftdrop.backend.modules.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.backend.modules.booking.service.TrackingNumberGenerator;
import com.swiftdrop.backend.modules.dispatch.entity.CourierAssignment;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.dispatch.service.DispatchService;
import com.swiftdrop.backend.modules.merchant.dto.CreateMerchantOrderRequest;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.backend.modules.merchant.exception.OrderCannotBeCancelledException;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.entity.ParcelSize;
import com.swiftdrop.backend.modules.parcel.entity.ServiceType;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.modules.zone.entity.Zone;

class MerchantOrderServiceTest {
    private static final UUID MERCHANT_ID = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID MERCHANT_USER_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID PARCEL_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID PICKUP_ZONE_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID DROPOFF_ZONE_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final String TRACKING = "SDP0000001";

    private final ParcelRepository parcelRepository = mock(ParcelRepository.class);
    private final MerchantPricingService pricingService = mock(MerchantPricingService.class);
    private final TrackingNumberGenerator trackingNumbers = mock(TrackingNumberGenerator.class);
    private final ParcelStatusService parcelStatusService = mock(ParcelStatusService.class);
    private final DispatchCourierAssignmentRepository assignments = mock(DispatchCourierAssignmentRepository.class);
    private final DispatchService dispatchService = mock(DispatchService.class);
    private final MerchantOrderService service = new MerchantOrderService(parcelRepository, pricingService,
            trackingNumbers, parcelStatusService, assignments, dispatchService);

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = merchant(MERCHANT_ID, MerchantRegistrationStatus.APPROVED);
        when(trackingNumbers.next()).thenReturn(TRACKING);
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(invocation -> {
            Parcel parcel = invocation.getArgument(0);
            ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
            return parcel;
        });
    }

    @Test
    void createsAMerchantParcelPricedByTheSharedServiceAndRecordsItsFirstStatus() {
        var pickup = zone(PICKUP_ZONE_ID, "Zone A");
        var dropoff = zone(DROPOFF_ZONE_ID, "Zone B");
        var fee = new FeeBreakdown(new BigDecimal("590.00"), new BigDecimal("150.00"), new BigDecimal("250.00"),
                new BigDecimal("990.00"), "LKR", Instant.parse("2026-11-20T12:30:00Z"));
        when(pricingService.quote(ServiceType.STANDARD, new BigDecimal("2.5"), "zone a", "zone b"))
                .thenReturn(new MerchantPricingService.Quote(pickup, dropoff, ParcelSize.MEDIUM, fee));

        var response = service.createOrder(merchant, orderRequest());

        var saved = ArgumentCaptor.forClass(Parcel.class);
        verify(parcelRepository).save(saved.capture());
        Parcel parcel = saved.getValue();
        assertThat(parcel.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(parcel.getCustomerId()).isNull();
        assertThat(parcel.getTrackingNumber()).isEqualTo(TRACKING);
        assertThat(parcel.getPickupCity()).isEqualTo("Zone A");
        assertThat(parcel.getDropoffZoneId()).isEqualTo(DROPOFF_ZONE_ID);
        assertThat(parcel.getFee()).isEqualByComparingTo("990.00");
        assertThat(parcel.getZoneCharge()).isEqualByComparingTo("250.00");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.getEstimatedDeliveryAt()).isEqualTo(Instant.parse("2026-11-20T12:30:00Z"));
        verify(parcelStatusService).recordCreation(parcel, MERCHANT_USER_ID);
        assertThat(response.getTrackingNumber()).isEqualTo(TRACKING);
    }

    @Test
    void unapprovedMerchantCannotBook() {
        var pending = merchant(MERCHANT_ID, MerchantRegistrationStatus.PENDING);

        assertThatThrownBy(() -> service.createOrder(pending, orderRequest()))
                .isInstanceOf(MerchantNotApprovedException.class);
        verify(parcelRepository, never()).save(any());
        verifyNoInteractions(parcelStatusService);
    }

    @Test
    void anotherMerchantsParcelIsNotFound() {
        when(parcelRepository.findByTrackingNumberAndMerchantId(TRACKING, MERCHANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(MERCHANT_ID, "sdp0000001")).isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> service.cancelOrder(merchant, TRACKING)).isInstanceOf(ParcelNotFoundException.class);
        verifyNoInteractions(parcelStatusService, dispatchService);
    }

    @Test
    void cancelReleasesTheCourierAssignmentAndGoesThroughTheStatusService() {
        var parcel = parcel(MERCHANT_ID, ParcelStatus.CREATED);
        ownedAndLocked(parcel);
        var assignment = mock(CourierAssignment.class);
        var assignmentId = UUID.fromString("90000000-0000-4000-8000-000000000001");
        when(assignment.getId()).thenReturn(assignmentId);
        when(assignments.findByParcelIdAndStatusIn(eq(PARCEL_ID), anyCollection())).thenReturn(Optional.of(assignment));

        service.cancelOrder(merchant, TRACKING);

        verify(dispatchService).cancelAssignment(assignmentId, MerchantOrderService.CANCEL_NOTE);
        verify(parcelStatusService).updateStatus(PARCEL_ID, ParcelStatus.CANCELLED, MerchantOrderService.CANCEL_NOTE,
                MERCHANT_USER_ID);
    }

    @Test
    void cancelWithoutAssignmentStillGoesThroughTheStatusService() {
        ownedAndLocked(parcel(MERCHANT_ID, ParcelStatus.CREATED));
        when(assignments.findByParcelIdAndStatusIn(eq(PARCEL_ID), anyCollection())).thenReturn(Optional.empty());

        service.cancelOrder(merchant, TRACKING);

        verifyNoInteractions(dispatchService);
        verify(parcelStatusService).updateStatus(PARCEL_ID, ParcelStatus.CANCELLED, MerchantOrderService.CANCEL_NOTE,
                MERCHANT_USER_ID);
    }

    @Test
    void parcelThatIsAlreadyPickedUpCannotBeCancelled() {
        ownedAndLocked(parcel(MERCHANT_ID, ParcelStatus.PICKED_UP));

        assertThatThrownBy(() -> service.cancelOrder(merchant, TRACKING))
                .isInstanceOf(OrderCannotBeCancelledException.class);
        verifyNoInteractions(parcelStatusService, dispatchService);
    }

    private void ownedAndLocked(Parcel parcel) {
        when(parcelRepository.findByTrackingNumberAndMerchantId(TRACKING, MERCHANT_ID)).thenReturn(Optional.of(parcel));
        when(parcelRepository.lockById(PARCEL_ID)).thenReturn(Optional.of(parcel));
    }

    private static CreateMerchantOrderRequest orderRequest() {
        return CreateMerchantOrderRequest.builder()
                .pickupAddress("1 Sample Street")
                .pickupCity("zone a")
                .recipientName("Test Recipient")
                .recipientPhone("0700000000")
                .dropoffAddress("2 Example Avenue")
                .dropoffCity("zone b")
                .serviceType(ServiceType.STANDARD)
                .weightKg(new BigDecimal("2.5"))
                .build();
    }

    private static Merchant merchant(UUID id, MerchantRegistrationStatus status) {
        return Merchant.builder().id(id).userId(MERCHANT_USER_ID).businessName("Test Shop")
                .registrationStatus(status).build();
    }

    private static Parcel parcel(UUID merchantId, ParcelStatus status) {
        var parcel = Parcel.builder()
                .trackingNumber(TRACKING)
                .merchantId(merchantId)
                .senderName("Test Shop")
                .recipientName("Test Recipient")
                .pickupAddress("1 Sample Street")
                .pickupCity("Zone A")
                .dropoffAddress("2 Example Avenue")
                .dropoffCity("Zone B")
                .serviceType(ServiceType.STANDARD)
                .weightKg(BigDecimal.ONE)
                .fee(new BigDecimal("590.00"))
                .status(status)
                .build();
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        return parcel;
    }

    private static Zone zone(UUID id, String name) {
        var zone = mock(Zone.class);
        when(zone.getId()).thenReturn(id);
        when(zone.getName()).thenReturn(name);
        return zone;
    }

}
