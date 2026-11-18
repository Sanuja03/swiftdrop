package com.swiftdrop.backend.modules.dispatch.repository;

import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.entity.CourierAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("dispatchCourierAssignmentRepository")
public interface DispatchCourierAssignmentRepository extends JpaRepository<CourierAssignment, UUID> {

    Optional<CourierAssignment> findByParcelIdAndStatusIn(UUID parcelId, Collection<AssignmentStatus> statuses);

    boolean existsByParcelIdAndStatusIn(UUID parcelId, Collection<AssignmentStatus> statuses);

    List<CourierAssignment> findByCourierIdAndStatusIn(UUID courierId, Collection<AssignmentStatus> statuses);

    List<CourierAssignment> findByCourierIdOrderByAssignedAtDesc(UUID courierId);

    long countByCourierIdAndStatusIn(UUID courierId, Collection<AssignmentStatus> statuses);

    List<CourierAssignment> findByParcelIdOrderByAssignedAtDesc(UUID parcelId);

    List<CourierAssignment> findByStatusInOrderByAssignedAtDesc(Collection<AssignmentStatus> statuses);

    List<CourierAssignment> findAllByOrderByAssignedAtDesc();

    @Query("""
        SELECT ca.courierId, COUNT(ca)
        FROM DispatchCourierAssignment ca
        WHERE ca.status IN :statuses
        GROUP BY ca.courierId
    """)
    List<Object[]> countActiveAssignmentsGroupedByCourier(@Param("statuses") Collection<AssignmentStatus> statuses);
}
