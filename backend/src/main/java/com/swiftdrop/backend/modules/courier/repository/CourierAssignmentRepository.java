package com.swiftdrop.backend.modules.courier.repository;

import com.swiftdrop.backend.modules.courier.entity.CourierAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierAssignmentRepository extends JpaRepository<CourierAssignment, UUID> {

    @EntityGraph(attributePaths = "parcel")
    List<CourierAssignment> findByCourierIdAndScheduledDateOrderByParcelId(UUID courierId, LocalDate scheduledDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CourierAssignment a where a.parcelId = :parcelId")
    Optional<CourierAssignment> lockByParcelId(UUID parcelId);
}
