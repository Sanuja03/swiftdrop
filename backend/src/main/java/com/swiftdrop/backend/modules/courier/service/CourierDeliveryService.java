package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.courier.dto.CourierDeliveryResponse;
import com.swiftdrop.backend.modules.courier.entity.Courier;
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
        var courier = couriers.requireCurrentCourier(principal);
        return assignments.findByCourierIdAndScheduledDateOrderByParcelId(courier.getId(),
                        LocalDate.now(clock.withZone(DELIVERY_ZONE)))
                .stream().map(CourierDeliveryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CourierDeliveryResponse details(UUID parcelId, Principal principal) {
        var courier = couriers.requireCurrentCourier(principal);
        return CourierDeliveryResponse.from(requireAssignment(assignments.findById(parcelId), courier));
    }

    @Transactional
    public void updateStatus(UUID parcelId, ParcelStatus status, String note, Principal principal) {
        var courier = couriers.requireCurrentCourier(principal);
        parcels.lockById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned parcel not found"));
        lockAssignment(parcelId, courier);
        if (status == ParcelStatus.DELIVERED && proofs.findCurrentByParcelId(parcelId)
                .filter(proof -> courier.getId().equals(proof.getCourierId())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Save delivery proof first");
        }
        statuses.updateStatus(parcelId, status, note, courier.getUserId());
    }

    CourierAssignment lockAssignment(UUID parcelId, Courier courier) {
        return requireAssignment(assignments.lockByParcelId(parcelId), courier);
    }

    private CourierAssignment requireAssignment(Optional<CourierAssignment> assignment, Courier courier) {
        return assignment.filter(value -> value.getCourierId().equals(courier.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned parcel not found"));
    }
}
