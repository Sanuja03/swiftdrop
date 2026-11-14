package com.swiftdrop.backend.modules.parcel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.support.TestParcels;

@ExtendWith(MockitoExtension.class)
class ParcelLookupServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    @Mock
    private ParcelRepository parcelRepository;

    @InjectMocks
    private ParcelLookupService service;

    @Test
    void findsAParcelByTrackingNumberInAnyLetterCase() {
        Parcel parcel = TestParcels.withId(UUID.randomUUID());
        when(parcelRepository.findByTrackingNumber(TestParcels.TRACKING_NUMBER)).thenReturn(Optional.of(parcel));

        assertThat(service.getByTrackingNumber(" sdp7845621 ")).isSameAs(parcel);
    }

    @Test
    void ownerGetsTheirParcel() {
        Parcel parcel = TestParcels.ownedBy(UUID.randomUUID(), OWNER);
        when(parcelRepository.findByTrackingNumber(TestParcels.TRACKING_NUMBER)).thenReturn(Optional.of(parcel));

        assertThat(service.getOwnedByCustomer(TestParcels.TRACKING_NUMBER, OWNER)).isSameAs(parcel);
    }

    @Test
    void anotherCustomersParcelIsReportedAsNotFound() {
        Parcel parcel = TestParcels.ownedBy(UUID.randomUUID(), OWNER);
        when(parcelRepository.findByTrackingNumber(TestParcels.TRACKING_NUMBER)).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> service.getOwnedByCustomer(TestParcels.TRACKING_NUMBER, UUID.randomUUID()))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void parcelWithoutAnOwnerBelongsToNobody() {
        Parcel parcel = TestParcels.ownedBy(UUID.randomUUID(), null);
        when(parcelRepository.findByTrackingNumber(TestParcels.TRACKING_NUMBER)).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> service.getOwnedByCustomer(TestParcels.TRACKING_NUMBER, OWNER))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void unknownTrackingNumberIsNotFound() {
        when(parcelRepository.findByTrackingNumber("SDP0000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnedByCustomer("SDP0000000", OWNER))
                .isInstanceOf(ParcelNotFoundException.class);
    }
}
