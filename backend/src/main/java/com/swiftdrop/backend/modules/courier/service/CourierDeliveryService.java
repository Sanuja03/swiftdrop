package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.courier.dto.CourierDeliveryResponse;
import com.swiftdrop.backend.modules.courier.entity.CourierAssignment;
import com.swiftdrop.backend.modules.courier.repository.CourierAssignmentRepository;
import com.swiftdrop.backend.modules.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierDeliveryService {

    private static final ZoneId DELIVERY_ZONE = ZoneId.of("Asia/Colombo");

    private final CourierService couriers;
    private final CourierAssignmentRepository assignments;
    private final DeliveryProofRepository proofs;
    private final ParcelRepository parcels;
    private final ParcelStatusService statuses;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CourierDeliveryResponse> today(Principal principal) {
        UUID courierUserId = couriers.requireCurrentCourierUserId(principal);
        return assignments.findToday(courierUserId,
                        LocalDate.now(clock.withZone(DELIVERY_ZONE)))
                .stream().map(CourierDeliveryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CourierDeliveryResponse details(UUID parcelId, Principal principal) {
        UUID courierUserId = couriers.requireCurrentCourierUserId(principal);
        return CourierDeliveryResponse.from(requireAssignment(assignments.findActiveByParcelId(parcelId), courierUserId));
    }

    @Transactional
    public void updateStatus(UUID parcelId, ParcelStatus status, String note, Principal principal) {
        UUID courierUserId = couriers.requireCurrentCourierUserId(principal);
        parcels.lockById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned parcel not found"));
        lockAssignment(parcelId, courierUserId);
        if (status == ParcelStatus.DELIVERED && proofs.findCurrentByParcelId(parcelId)
                .filter(proof -> courierUserId.equals(proof.getCourierId())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Save delivery proof first");
        }
        statuses.updateStatus(parcelId, status, note, courierUserId);
    }

    CourierAssignment lockAssignment(UUID parcelId, UUID courierUserId) {
        return requireAssignment(assignments.lockByParcelId(parcelId), courierUserId);
    }

    private CourierAssignment requireAssignment(Optional<CourierAssignment> assignment, UUID courierUserId) {
        return assignment.filter(value -> value.getCourierId().equals(courierUserId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned parcel not found"));
    }
}
