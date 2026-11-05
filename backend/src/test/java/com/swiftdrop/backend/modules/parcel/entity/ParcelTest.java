package com.swiftdrop.backend.modules.parcel.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;

class ParcelTest {

    @Test
    void newParcelStartsAsCreated() {
        assertThat(Parcel.builder().build().getStatus()).isEqualTo(ParcelStatus.CREATED);
    }

    @Test
    void changeStatusMovesForwardAndReturnsPreviousStatus() {
        Parcel parcel = Parcel.builder().build();

        ParcelStatus previous = parcel.changeStatus(ParcelStatus.PICKED_UP);

        assertThat(previous).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
    }

    @Test
    void changeStatusRejectsInvalidTransitionAndKeepsCurrentStatus() {
        Parcel parcel = Parcel.builder().build();

        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.DELIVERED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
    }
}
