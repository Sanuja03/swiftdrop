package com.swiftdrop.backend.modules.booking.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.booking.customer.BookingCustomer;
import com.swiftdrop.backend.modules.booking.customer.CurrentCustomerProvider;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelRequest;
import com.swiftdrop.backend.modules.booking.dto.CreateParcelResponse;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.pricing.dto.FeeBreakdown;
import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.pricing.service.PricingService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

import lombok.RequiredArgsConstructor;

/**
 * Books a delivery: prices it through PricingService, saves the parcel as CREATED and
 * records the first timeline entry through the tracking module's ParcelStatusService.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    /** Matches parcels.sender_name. */
    static final int SENDER_NAME_MAX_LENGTH = 120;

    private final ZoneRepository zoneRepository;
    private final PricingService pricingService;
    private final ParcelRepository parcelRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final ParcelStatusService parcelStatusService;

    /**
     * Saves the parcel, its fee breakdown and its first status entry in one transaction.
     *
     * @throws PricingException if a zone is unknown or inactive, or the service cannot be priced
     * @throws com.swiftdrop.backend.modules.booking.exception.BookingException if no customer
     *         or tracking number is available
     */
    @Transactional
    public CreateParcelResponse createParcel(CreateParcelRequest request) {
        Zone pickupZone = activeZone(request.pickupZoneId());
        Zone dropoffZone = activeZone(request.dropoffZoneId());
        FeeBreakdown fee = pricingService.estimate(request.toEstimateRequest());
        BookingCustomer customer = currentCustomerProvider.currentCustomer();

        Parcel parcel = parcelRepository.save(Parcel.builder()
                .trackingNumber(trackingNumberGenerator.next())
                .customerId(customer.id())
                .senderName(senderName(customer))
                .recipientName(request.recipientName().strip())
                .recipientPhone(request.recipientPhone().strip())
                .pickupAddress(request.pickupAddress().strip())
                .pickupCity(pickupZone.getName())
                .pickupZoneId(pickupZone.getId())
                .dropoffAddress(request.dropoffAddress().strip())
                .dropoffCity(dropoffZone.getName())
                .dropoffZoneId(dropoffZone.getId())
                .parcelSize(request.parcelSize())
                .serviceType(request.serviceType())
                .weightKg(request.weightKg())
                .baseFee(fee.baseFee())
                .weightCharge(fee.weightCharge())
                .zoneCharge(fee.zoneCharge())
                .fee(fee.totalFee())
                .status(ParcelStatus.CREATED)
                .estimatedDeliveryAt(fee.estimatedDeliveryAt())
                .build());

        parcelStatusService.recordCreation(parcel, customer.id());
        return CreateParcelResponse.from(parcel, fee.currency());
    }

    private Zone activeZone(UUID zoneId) {
        return zoneRepository.findByIdAndActiveTrue(zoneId)
                .orElseThrow(() -> PricingException.unknownZone(zoneId));
    }

    private static String senderName(BookingCustomer customer) {
        String name = customer.fullName() == null ? "" : customer.fullName().strip();
        return name.length() > SENDER_NAME_MAX_LENGTH ? name.substring(0, SENDER_NAME_MAX_LENGTH) : name;
    }
}
