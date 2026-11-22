package com.swiftdrop.backend.modules.dispatch.listener;

import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.repository.DispatchCourierAssignmentRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Listens to parcel status change events and marks any active courier assignment
 * (ASSIGNED or ACCEPTED) as COMPLETED when the parcel becomes DELIVERED or FAILED.
 *
 * <p>Runs synchronously inside {@code ParcelStatusService.updateStatus()}'s transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ParcelStatusDispatchListener {

    private static final Set<AssignmentStatus> ACTIVE_STATUSES = Set.of(
            AssignmentStatus.ASSIGNED,
            AssignmentStatus.ACCEPTED
    );

    private final DispatchCourierAssignmentRepository courierAssignmentRepository;

    @EventListener
    public void onParcelStatusChanged(ParcelStatusChangedEvent event) {
        if (event == null || event.newStatus() == null) {
            return;
        }

        ParcelStatus newStatus = event.newStatus();
        if (newStatus != ParcelStatus.DELIVERED && newStatus != ParcelStatus.FAILED) {
            return;
        }

        courierAssignmentRepository.findByParcelIdAndStatusIn(event.parcelId(), ACTIVE_STATUSES)
                .ifPresent(assignment -> {
                    assignment.complete();
                    courierAssignmentRepository.save(assignment);
                    log.info("Marked courier assignment [{}] as COMPLETED for parcel [{}] on terminal status [{}]",
                            assignment.getId(), event.parcelId(), newStatus);
                });
    }
}
