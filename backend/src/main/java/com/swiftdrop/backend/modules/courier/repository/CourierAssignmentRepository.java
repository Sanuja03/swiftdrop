package com.swiftdrop.backend.modules.courier.repository;

import com.swiftdrop.backend.modules.courier.entity.CourierAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("courierAssignmentViewRepository")
public interface CourierAssignmentRepository extends JpaRepository<CourierAssignment, UUID> {

    String ACTIVE = " and a.status in ('ASSIGNED', 'ACCEPTED')";

    @EntityGraph(attributePaths = "parcel")
    @Query("select a from CourierAssignmentView a where a.courierId = :courierId"
            + " and a.parcel.estimatedDeliveryAt >= :start and a.parcel.estimatedDeliveryAt < :end"
            + ACTIVE + " order by a.parcel.estimatedDeliveryAt, a.parcelId")
    List<CourierAssignment> findToday(UUID courierId, Instant start, Instant end);

    @Query("select a from CourierAssignmentView a where a.parcelId = :parcelId" + ACTIVE)
    Optional<CourierAssignment> findActiveByParcelId(UUID parcelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CourierAssignmentView a where a.parcelId = :parcelId" + ACTIVE)
    Optional<CourierAssignment> lockByParcelId(UUID parcelId);
}
