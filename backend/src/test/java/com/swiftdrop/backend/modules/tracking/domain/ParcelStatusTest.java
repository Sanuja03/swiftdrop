package com.swiftdrop.backend.modules.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException;

class ParcelStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "CREATED, PICKED_UP",
            "PICKED_UP, IN_TRANSIT",
            "IN_TRANSIT, OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY, DELIVERED",
            "OUT_FOR_DELIVERY, FAILED"
    })
    void allowsTheNextStepInTheLifecycle(ParcelStatus from, ParcelStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
        assertThatCode(() -> from.assertCanTransitionTo(to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "CREATED, DELIVERED",
            "CREATED, IN_TRANSIT",
            "PICKED_UP, CREATED",
            "IN_TRANSIT, FAILED",
            "DELIVERED, CREATED",
            "FAILED, OUT_FOR_DELIVERY"
    })
    void rejectsSkippingOrGoingBackwards(ParcelStatus from, ParcelStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> from.assertCanTransitionTo(to))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @ParameterizedTest
    @EnumSource(ParcelStatus.class)
    void neverAllowsStayingOnTheSameStatus(ParcelStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ParcelStatus.class)
    void rejectsNullTarget(ParcelStatus status) {
        assertThat(status.canTransitionTo(null)).isFalse();
    }

    @Test
    void onlyDeliveredAndFailedAreTerminal() {
        EnumSet<ParcelStatus> terminal = EnumSet.noneOf(ParcelStatus.class);
        for (ParcelStatus status : ParcelStatus.values()) {
            if (status.isTerminal()) {
                terminal.add(status);
            }
        }
        assertThat(terminal).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
    }

    @Test
    void everyStatusHasAHumanReadableLabel() {
        for (ParcelStatus status : ParcelStatus.values()) {
            assertThat(status.getLabel()).isNotBlank();
        }
    }
}
