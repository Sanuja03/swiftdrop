package com.swiftdrop.backend.modules.dispatch.service;

import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.dispatch.dto.AssignCourierRequest;
import com.swiftdrop.backend.modules.dispatch.dto.AssignmentResponse;
import com.swiftdrop.backend.modules.dispatch.dto.CourierWorkloadResponse;
import com.swiftdrop.backend.modules.parcel.entity.Parcel;

import java.util.List;
import java.util.UUID;

public interface DispatchService {

    /**
     * Retrieves parcels with status CREATED that have no active courier assignment
     * (i.e. neither ASSIGNED nor ACCEPTED).
     *
     * @return list of unassigned parcels ordered by creation date descending
     */
    List<Parcel> getUnassignedParcels();

    /**
     * Retrieves all active users with role ROLE_COURIER.
     *
     * @return list of active couriers
     */
    List<User> getActiveCouriers();

    /**
     * Assigns a courier to an unassigned parcel.
     *
     * @param request      the assignment request containing parcelId, courierId, and optional notes
     * @param dispatcherId the ID of the authenticated dispatcher creating the assignment
     * @return the created assignment response
     */
    AssignmentResponse assignCourier(AssignCourierRequest request, UUID dispatcherId);

    /**
     * Retrieves the current workload for all active couriers, including those with zero assignments.
     * Workload counts include only ASSIGNED and ACCEPTED assignments.
     *
     * @return list of courier workloads
     */
    List<CourierWorkloadResponse> getCourierWorkloads();

    /**
     * Cancels an active courier assignment.
     *
     * @param assignmentId the assignment to cancel
     * @param reason       optional cancellation reason
     * @return the updated assignment response
     */
    AssignmentResponse cancelAssignment(UUID assignmentId, String reason);

    /**
     * Retrieves all assignments belonging to the specified courier ordered by assigned date descending.
     *
     * @param courierId the ID of the authenticated courier
     * @return list of assignments for the courier
     */
    List<AssignmentResponse> getAssignmentsForCourier(UUID courierId);

    /**
     * Accepts an assigned parcel delivery.
     *
     * @param assignmentId the assignment ID to accept
     * @param courierId    the ID of the authenticated courier accepting the assignment
     * @return the updated assignment response
     */
    AssignmentResponse acceptAssignment(UUID assignmentId, UUID courierId);

    /**
     * Rejects an assigned parcel delivery.
     *
     * @param assignmentId the assignment ID to reject
     * @param courierId    the ID of the authenticated courier rejecting the assignment
     * @param reason       optional rejection reason
     * @return the updated assignment response
     */
    AssignmentResponse rejectAssignment(UUID assignmentId, UUID courierId, String reason);

    /**
     * Marks an accepted assignment as completed upon parcel delivery.
     *
     * @param assignmentId the assignment ID to complete
     * @param courierId    the ID of the authenticated courier completing the assignment
     * @return the updated assignment response
     */
    AssignmentResponse completeAssignment(UUID assignmentId, UUID courierId);
}
