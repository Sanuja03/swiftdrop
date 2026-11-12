package com.swiftdrop.backend.modules.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.swiftdrop.backend.modules.booking.exception.BookingException;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;

class TrackingNumberGeneratorTest {
    private static final int BOUND = 10_000_000;

    private final ParcelRepository parcelRepository = mock(ParcelRepository.class);
    private final RandomGenerator random = mock(RandomGenerator.class);
    private final TrackingNumberGenerator generator = new TrackingNumberGenerator(parcelRepository, random);

    @Test
    void prefixesSdpAndPadsToSevenDigits() {
        when(random.nextInt(BOUND)).thenReturn(42);
        when(parcelRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());

        String number = generator.next();

        assertThat(number).isEqualTo("SDP0000042");
        assertThat(number).matches(ParcelLookupService.TRACKING_NUMBER_PATTERN);
    }

    @Test
    void triesAgainWhenNumberIsTaken() {
        when(random.nextInt(BOUND)).thenReturn(1234567, 7654321);
        when(parcelRepository.findByTrackingNumber("SDP1234567")).thenReturn(Optional.of(mock(Parcel.class)));
        when(parcelRepository.findByTrackingNumber("SDP7654321")).thenReturn(Optional.empty());

        assertThat(generator.next()).isEqualTo("SDP7654321");
        verify(parcelRepository, times(2)).findByTrackingNumber(anyString());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(random.nextInt(BOUND)).thenReturn(1);
        when(parcelRepository.findByTrackingNumber(anyString())).thenReturn(Optional.of(mock(Parcel.class)));

        assertThatThrownBy(generator::next).isInstanceOf(BookingException.class);
        verify(parcelRepository, times(TrackingNumberGenerator.MAX_ATTEMPTS)).findByTrackingNumber(anyString());
    }

    @Test
    void defaultRandomSourceProducesValidNumbers() {
        when(parcelRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        var realGenerator = new TrackingNumberGenerator(parcelRepository);

        for (int i = 0; i < 50; i++) {
            assertThat(realGenerator.next()).matches("^SDP\\d{7}$");
        }
    }
}
