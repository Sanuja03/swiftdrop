package com.swiftdrop.backend.modules.parcel.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;

import lombok.RequiredArgsConstructor;

/**
 * Finds parcels by the tracking number customers type in.
 */
@Service
@RequiredArgsConstructor
public class ParcelLookupService {

    /** Accepted tracking number format: 6 to 32 letters, digits or dashes. */
    public static final String TRACKING_NUMBER_PATTERN = "^[A-Za-z0-9-]{6,32}$";

    private final ParcelRepository parcelRepository;

    /**
     * @throws ParcelNotFoundException if no parcel has this tracking number
     */
    @Transactional(readOnly = true)
    public Parcel getByTrackingNumber(String trackingNumber) {
        String normalised = normalise(trackingNumber);
        return parcelRepository.findByTrackingNumber(normalised)
                .orElseThrow(() -> new ParcelNotFoundException(normalised));
    }

    /**
     * Finds a parcel only if it belongs to this customer. Someone else's parcel is reported
     * as not found, so a tracking number never reveals that another customer's parcel exists.
     *
     * @throws ParcelNotFoundException if no parcel has this tracking number or it belongs to someone else
     */
    @Transactional(readOnly = true)
    public Parcel getOwnedByCustomer(String trackingNumber, UUID customerId) {
        String normalised = normalise(trackingNumber);
        return parcelRepository.findByTrackingNumber(normalised)
                .filter(parcel -> customerId != null && customerId.equals(parcel.getCustomerId()))
                .orElseThrow(() -> new ParcelNotFoundException(normalised));
    }

    /** Tracking numbers are stored in upper case; customers may type them in any case. */
    public static String normalise(String trackingNumber) {
        return trackingNumber == null ? "" : trackingNumber.strip().toUpperCase(Locale.ROOT);
    }
}
