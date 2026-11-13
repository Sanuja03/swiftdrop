package com.swiftdrop.backend.modules.booking.service;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.booking.exception.BookingException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;

/**
 * Creates tracking numbers such as {@code SDP0420517}: "SDP" followed by 7 random digits.
 * Tries again when a number is already taken; the UNIQUE constraint on
 * parcels.tracking_number is the final guard against a race between two bookings.
 */
@Component
public class TrackingNumberGenerator {

    static final String PREFIX = "SDP";
    static final int DIGITS = 7;
    static final int MAX_ATTEMPTS = 10;
    private static final int BOUND = 10_000_000;

    private final ParcelRepository parcelRepository;
    private final RandomGenerator random;

    @Autowired
    public TrackingNumberGenerator(ParcelRepository parcelRepository) {
        this(parcelRepository, new SecureRandom());
    }

    TrackingNumberGenerator(ParcelRepository parcelRepository, RandomGenerator random) {
        this.parcelRepository = parcelRepository;
        this.random = random;
    }

    /**
     * @throws BookingException if no free number was found after {@link #MAX_ATTEMPTS} tries
     */
    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = PREFIX + String.format("%0" + DIGITS + "d", random.nextInt(BOUND));
            if (parcelRepository.findByTrackingNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw BookingException.trackingNumberUnavailable();
    }
}
