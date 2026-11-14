package com.swiftdrop.backend.modules.dispatch.service.impl;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.dispatch.dto.AssignCourierRequest;
import com.swiftdrop.backend.modules.dispatch.dto.AssignmentResponse;
import com.swiftdrop.backend.modules.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.entity.CourierAssignment;
import com.swiftdrop.backend.modules.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.backend.modules.dispatch.exception.DuplicateAssignmentException;
import com.swiftdrop.backend.modules.dispatch.exception.InvalidAssignmentStateException;
import com.swiftdrop.backend.modules.dispatch.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.modules.dispatch.service.DispatchService;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DispatchServiceImpl implements DispatchService {

    private static final Set<AssignmentStatus> ACTIVE_STATUSES = Set.of(
            AssignmentStatus.ASSIGNED,
            AssignmentStatus.ACCEPTED
    );

    private final ParcelRepository parcelRepository;
    private final CourierAssignmentRepository courierAssignmentRepository;
    private final UserRepository userRepository;

    @Override
    public List<Parcel> getUnassignedParcels() {
        List<Parcel> createdParcels = parcelRepository.findByStatusOrderByCreatedAtDesc(ParcelStatus.CREATED);

        List<CourierAssignment> activeAssignments = courierAssignmentRepository
                .findByStatusInOrderByAssignedAtDesc(ACTIVE_STATUSES);

        Set<UUID> activeParcelIds = activeAssignments.stream()
                .map(CourierAssignment::getParcelId)
                .collect(Collectors.toSet());

        return createdParcels.stream()
                .filter(parcel -> !activeParcelIds.contains(parcel.getId()))
                .toList();
    }

    @Override
    public List<User> getActiveCouriers() {
        return userRepository.findByRole(Role.ROLE_COURIER).stream()
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .toList();
    }

    @Override
    @Transactional
    public AssignmentResponse assignCourier(AssignCourierRequest request, UUID dispatcherId) {
        if (request == null || request.getParcelId() == null || request.getCourierId() == null) {
            throw new IllegalArgumentException("Assignment request, parcel ID, and courier ID must not be null");
        }
        if (dispatcherId == null) {
            throw new IllegalArgumentException("Dispatcher ID must not be null");
        }

        // 1. Validate Parcel exists and is in CREATED status
        Parcel parcel = parcelRepository.findById(request.getParcelId())
                .orElseThrow(() -> new ParcelNotFoundException(request.getParcelId()));

        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new InvalidAssignmentStateException(
                    null,
                    "assign: parcel is in status " + parcel.getStatus() + " (only CREATED parcels can be assigned)"
            );
        }

        // 2. Validate Courier exists, has ROLE_COURIER, and is ACTIVE
        User courier = userRepository.findById(request.getCourierId())
                .orElseThrow(() -> new UsernameNotFoundException("Courier not found with id: " + request.getCourierId()));

        if (courier.getRole() != Role.ROLE_COURIER) {
            throw new IllegalArgumentException("User " + courier.getId() + " is not a courier");
        }
        if (courier.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Courier account is not active: " + courier.getId());
        }

        // 3. Validate Dispatcher exists and has dispatcher/admin privileges
        User dispatcher = userRepository.findById(dispatcherId)
                .orElseThrow(() -> new UsernameNotFoundException("Dispatcher not found with id: " + dispatcherId));

        if (dispatcher.getRole() != Role.ROLE_DISPATCHER && dispatcher.getRole() != Role.ROLE_ADMIN) {
            throw new IllegalArgumentException("User " + dispatcherId + " is not authorized to dispatch parcels");
        }

        // 4. Pre-check active assignment uniqueness in service layer
        if (courierAssignmentRepository.existsByParcelIdAndStatusIn(request.getParcelId(), ACTIVE_STATUSES)) {
            throw new DuplicateAssignmentException(request.getParcelId());
        }

        // 5. Create assignment (preserves parcel status)
        CourierAssignment assignment = CourierAssignment.builder()
                .parcelId(parcel.getId())
                .courierId(courier.getId())
                .dispatcherId(dispatcher.getId())
                .status(AssignmentStatus.ASSIGNED)
                .notes(request.getNotes())
                .build();

        try {
            CourierAssignment saved = courierAssignmentRepository.saveAndFlush(assignment);
            log.info("Assigned parcel [{}] to courier [{}] by dispatcher [{}]",
                    parcel.getId(), courier.getId(), dispatcher.getId());
            return AssignmentResponse.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isActiveAssignmentConstraintViolation(ex)) {
                log.warn("Concurrent duplicate active assignment prevented for parcel [{}]", request.getParcelId());
                throw new DuplicateAssignmentException(request.getParcelId());
            }
            throw ex;
        }
    }

    @Override
    public List<CourierWorkloadResponse> getCourierWorkloads() {
        List<User> activeCouriers = getActiveCouriers();

        List<Object[]> groupedCounts = courierAssignmentRepository
                .countActiveAssignmentsGroupedByCourier(ACTIVE_STATUSES);

        Map<UUID, Long> countMap = groupedCounts.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                ));

        return activeCouriers.stream().map(courier -> {
            long activeCount = countMap.getOrDefault(courier.getId(), 0L);
            String workloadStatus = determineWorkloadStatus(activeCount);
            return CourierWorkloadResponse.builder()
                    .courierId(courier.getId())
                    .courierName(courier.getFullName())
                    .courierEmail(courier.getEmail())
                    .courierPhone(courier.getPhone())
                    .activeAssignmentsCount(activeCount)
                    .workloadStatus(workloadStatus)
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public AssignmentResponse cancelAssignment(UUID assignmentId, String reason) {
        if (assignmentId == null) {
            throw new IllegalArgumentException("Assignment ID must not be null");
        }

        CourierAssignment assignment = courierAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));

        if (!assignment.getStatus().isActive()) {
            throw new InvalidAssignmentStateException(assignment.getStatus(), "cancel");
        }

        assignment.cancel(reason);
        CourierAssignment updated = courierAssignmentRepository.save(assignment);
        log.info("Cancelled assignment [{}] for parcel [{}] with reason: {}",
                assignment.getId(), assignment.getParcelId(), reason);

        return AssignmentResponse.fromEntity(updated);
    }

    @Override
    public List<AssignmentResponse> getAssignmentsForCourier(UUID courierId) {
        if (courierId == null) {
            throw new IllegalArgumentException("Courier ID must not be null");
        }
        return courierAssignmentRepository.findByCourierIdOrderByAssignedAtDesc(courierId).stream()
                .map(AssignmentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public AssignmentResponse acceptAssignment(UUID assignmentId, UUID courierId) {
        if (assignmentId == null || courierId == null) {
            throw new IllegalArgumentException("Assignment ID and Courier ID must not be null");
        }

        CourierAssignment assignment = courierAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));

        if (!assignment.getCourierId().equals(courierId)) {
            throw new AccessDeniedException("Courier is not authorized to modify this assignment: " + assignmentId);
        }

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new InvalidAssignmentStateException(assignment.getStatus(), "accept");
        }

        assignment.accept();
        CourierAssignment updated = courierAssignmentRepository.save(assignment);
        log.info("Courier [{}] accepted assignment [{}] for parcel [{}]",
                courierId, assignment.getId(), assignment.getParcelId());

        return AssignmentResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public AssignmentResponse rejectAssignment(UUID assignmentId, UUID courierId, String reason) {
        if (assignmentId == null || courierId == null) {
            throw new IllegalArgumentException("Assignment ID and Courier ID must not be null");
        }

        CourierAssignment assignment = courierAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));

        if (!assignment.getCourierId().equals(courierId)) {
            throw new AccessDeniedException("Courier is not authorized to modify this assignment: " + assignmentId);
        }

        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new InvalidAssignmentStateException(assignment.getStatus(), "reject");
        }

        assignment.reject(reason);
        CourierAssignment updated = courierAssignmentRepository.save(assignment);
        log.info("Courier [{}] rejected assignment [{}] for parcel [{}] with reason: {}",
                courierId, assignment.getId(), assignment.getParcelId(), reason);

        return AssignmentResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public AssignmentResponse completeAssignment(UUID assignmentId, UUID courierId) {
        if (assignmentId == null || courierId == null) {
            throw new IllegalArgumentException("Assignment ID and Courier ID must not be null");
        }

        CourierAssignment assignment = courierAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));

        if (!assignment.getCourierId().equals(courierId)) {
            throw new AccessDeniedException("Courier is not authorized to modify this assignment: " + assignmentId);
        }

        if (assignment.getStatus() != AssignmentStatus.ACCEPTED) {
            throw new InvalidAssignmentStateException(assignment.getStatus(), "complete");
        }

        assignment.complete();
        CourierAssignment updated = courierAssignmentRepository.save(assignment);
        log.info("Courier [{}] completed assignment [{}] for parcel [{}]",
                courierId, assignment.getId(), assignment.getParcelId());

        return AssignmentResponse.fromEntity(updated);
    }

    private String determineWorkloadStatus(long activeCount) {
        if (activeCount == 0) {
            return "AVAILABLE";
        } else if (activeCount <= 3) {
            return "MODERATE";
        } else {
            return "BUSY";
        }
    }

    private boolean isActiveAssignmentConstraintViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve) {
                if (cve.getConstraintName() != null
                        && cve.getConstraintName().equalsIgnoreCase("idx_active_courier_assignment")) {
                    return true;
                }
            }
            current = current.getCause();
        }

        String exMessage = (ex.getMessage() != null) ? ex.getMessage() : "";
        Throwable rootCause = ex.getRootCause();
        String rootMessage = (rootCause != null && rootCause.getMessage() != null) ? rootCause.getMessage() : "";
        String combined = (exMessage + " " + rootMessage).toLowerCase();

        return combined.contains("idx_active_courier_assignment");
    }
}
