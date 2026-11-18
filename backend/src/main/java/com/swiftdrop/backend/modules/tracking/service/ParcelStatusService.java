package com.swiftdrop.backend.modules.tracking.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.modules.tracking.exception.ParcelStatusRequirementException;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.entity.ParcelStatusHistory;
import com.swiftdrop.backend.modules.tracking.event.ParcelStatusChangedEvent;
import com.swiftdrop.backend.modules.tracking.repository.ParcelStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * The single place where a parcel's status changes. Every caller (courier app,
 * dispatcher, admin) goes through here, so the rules are never duplicated.
 */
@Service
@RequiredArgsConstructor
public class ParcelStatusService {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final DeliveryProofRepository deliveryProofs;

    /**
     * Records the first timeline entry for a parcel that has just been saved as CREATED.
     * Call this from the booking flow right after the parcel is persisted.
     */
    @Transactional
    public void recordCreation(Parcel parcel, UUID createdBy) {
        record(parcel, null, parcel.getStatus(), null, createdBy);
    }

    /**
     * Moves a parcel to a new status, saves the change in its history and announces it.
     * The status, the history row and the event succeed or fail together.
     *
     * @throws ParcelNotFoundException if no parcel has this id
     * @throws com.swiftdrop.backend.modules.tracking.exception.InvalidStatusTransitionException
     *         if the lifecycle does not allow the change
     */
    @Transactional
    public void updateStatus(UUID parcelId, ParcelStatus newStatus, String note, UUID changedBy) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));

        parcel.getStatus().assertCanTransitionTo(newStatus);
        if (newStatus == ParcelStatus.FAILED && (note == null || note.isBlank() || note.strip().length() > 500)) {
            throw new ParcelStatusRequirementException("Failed delivery requires a reason of up to 500 characters");
        }
        if (newStatus == ParcelStatus.DELIVERED && !deliveryProofs.existsByParcelId(parcelId)) {
            throw new ParcelStatusRequirementException("Save delivery proof before marking the parcel delivered");
        }
        ParcelStatus oldStatus = parcel.changeStatus(newStatus);
        record(parcel, oldStatus, newStatus, note, changedBy);
    }

    private void record(Parcel parcel, ParcelStatus oldStatus, ParcelStatus newStatus,
                        String note, UUID changedBy) {
        Instant now = clock.instant();
        String cleanNote = normalise(note);

        historyRepository.save(new ParcelStatusHistory(parcel, oldStatus, newStatus, cleanNote, changedBy, now));
        eventPublisher.publishEvent(new ParcelStatusChangedEvent(
                parcel.getId(), parcel.getTrackingNumber(), oldStatus, newStatus, cleanNote, changedBy, now));
    }

    private static String normalise(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }
}
