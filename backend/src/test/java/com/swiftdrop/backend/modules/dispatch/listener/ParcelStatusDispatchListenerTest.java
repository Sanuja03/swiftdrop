package com.swiftdrop.backend.modules.dispatch.listener;

import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.entity.CourierAssignment;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelStatusDispatchListenerTest {

    private static final UUID PARCEL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID COURIER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DISPATCHER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Set<AssignmentStatus> ACTIVE_STATUSES = Set.of(
            AssignmentStatus.ASSIGNED,
            AssignmentStatus.ACCEPTED
    );

    @Mock
    private DispatchCourierAssignmentRepository courierAssignmentRepository;

    @InjectMocks
    private ParcelStatusDispatchListener listener;

    @Test
    @DisplayName("onParcelStatusChanged marks ACCEPTED assignment as COMPLETED when parcel is DELIVERED")
    void onParcelStatusChanged_marksAcceptedAssignmentCompleted_whenDelivered() {
        CourierAssignment assignment = buildAssignment(AssignmentStatus.ACCEPTED);
        when(courierAssignmentRepository.findByParcelIdAndStatusIn(PARCEL_ID, ACTIVE_STATUSES))
                .thenReturn(Optional.of(assignment));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                PARCEL_ID, "SWD-123456", ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED,
                "Delivered successfully", COURIER_ID, Instant.now()
        );

        listener.onParcelStatusChanged(event);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        verify(courierAssignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("onParcelStatusChanged marks ASSIGNED assignment as COMPLETED when parcel is DELIVERED")
    void onParcelStatusChanged_marksAssignedAssignmentCompleted_whenDelivered() {
        CourierAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
        when(courierAssignmentRepository.findByParcelIdAndStatusIn(PARCEL_ID, ACTIVE_STATUSES))
                .thenReturn(Optional.of(assignment));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                PARCEL_ID, "SWD-123456", ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED,
                "Delivered successfully", COURIER_ID, Instant.now()
        );

        listener.onParcelStatusChanged(event);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        verify(courierAssignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("onParcelStatusChanged marks ACCEPTED assignment as COMPLETED when parcel is FAILED")
    void onParcelStatusChanged_marksAcceptedAssignmentCompleted_whenFailed() {
        CourierAssignment assignment = buildAssignment(AssignmentStatus.ACCEPTED);
        when(courierAssignmentRepository.findByParcelIdAndStatusIn(PARCEL_ID, ACTIVE_STATUSES))
                .thenReturn(Optional.of(assignment));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                PARCEL_ID, "SWD-123456", ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED,
                "Customer not available", COURIER_ID, Instant.now()
        );

        listener.onParcelStatusChanged(event);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        verify(courierAssignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("onParcelStatusChanged marks ASSIGNED assignment as COMPLETED when parcel is FAILED")
    void onParcelStatusChanged_marksAssignedAssignmentCompleted_whenFailed() {
        CourierAssignment assignment = buildAssignment(AssignmentStatus.ASSIGNED);
        when(courierAssignmentRepository.findByParcelIdAndStatusIn(PARCEL_ID, ACTIVE_STATUSES))
                .thenReturn(Optional.of(assignment));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                PARCEL_ID, "SWD-123456", ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED,
                "Delivery rejected at door", COURIER_ID, Instant.now()
        );

        listener.onParcelStatusChanged(event);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        verify(courierAssignmentRepository).save(assignment);
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatus.class, names = {"CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "CANCELLED"})
    @DisplayName("onParcelStatusChanged ignores non-terminal parcel statuses")
    void onParcelStatusChanged_ignoresNonTerminalStatuses(ParcelStatus nonTerminalStatus) {
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                PARCEL_ID, "SWD-123456", ParcelStatus.CREATED, nonTerminalStatus,
                "Progress update", COURIER_ID, Instant.now()
        );

        listener.onParcelStatusChanged(event);

        verifyNoInteractions(courierAssignmentRepository);
    }

    @Test
    @DisplayName("onParcelStatusChanged handles gracefully when parcel has no active assignment")
    void onParcelStatusChanged_handlesGracefullyWhenNoActiveAssignment() {
        when(courierAssignmentRepository.findByParcelIdAndStatusIn(PARCEL_ID, ACTIVE_STATUSES))
                .thenReturn(Optional.empty());

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                PARCEL_ID, "SWD-123456", ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED,
                "Delivered", COURIER_ID, Instant.now()
        );

        listener.onParcelStatusChanged(event);

        verify(courierAssignmentRepository).findByParcelIdAndStatusIn(PARCEL_ID, ACTIVE_STATUSES);
        verify(courierAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("onParcelStatusChanged handles null event or null newStatus safely")
    void onParcelStatusChanged_handlesNullSafely() {
        listener.onParcelStatusChanged(null);

        ParcelStatusChangedEvent eventWithNullStatus = new ParcelStatusChangedEvent(
                PARCEL_ID, "SWD-123456", ParcelStatus.OUT_FOR_DELIVERY, null,
                null, COURIER_ID, Instant.now()
        );
        listener.onParcelStatusChanged(eventWithNullStatus);

        verifyNoInteractions(courierAssignmentRepository);
    }

    @Test
    @DisplayName("CourierAssignment.complete() allows ASSIGNED and ACCEPTED, rejects non-active statuses")
    void courierAssignment_complete_validatesLifecycle() {
        CourierAssignment assigned = buildAssignment(AssignmentStatus.ASSIGNED);
        assigned.complete();
        assertThat(assigned.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);

        CourierAssignment accepted = buildAssignment(AssignmentStatus.ACCEPTED);
        accepted.complete();
        assertThat(accepted.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);

        CourierAssignment cancelled = buildAssignment(AssignmentStatus.CANCELLED);
        assertThatThrownBy(cancelled::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete non-active assignment in status: CANCELLED");

        CourierAssignment rejected = buildAssignment(AssignmentStatus.REJECTED);
        assertThatThrownBy(rejected::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete non-active assignment in status: REJECTED");

        CourierAssignment completed = buildAssignment(AssignmentStatus.COMPLETED);
        assertThatThrownBy(completed::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete non-active assignment in status: COMPLETED");
    }

    private CourierAssignment buildAssignment(AssignmentStatus status) {
        return CourierAssignment.builder()
                .id(ASSIGNMENT_ID)
                .parcelId(PARCEL_ID)
                .courierId(COURIER_ID)
                .dispatcherId(DISPATCHER_ID)
                .status(status)
                .notes("Initial assignment")
                .build();
    }
}
