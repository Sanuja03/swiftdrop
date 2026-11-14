package com.swiftdrop.backend.modules.dispatch.dto;

import com.swiftdrop.backend.modules.dispatch.entity.AssignmentStatus;
import com.swiftdrop.backend.modules.dispatch.entity.CourierAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private UUID id;
    private UUID parcelId;
    private UUID courierId;
    private UUID dispatcherId;
    private AssignmentStatus status;
    private String notes;
    private Instant assignedAt;
    private Instant updatedAt;
    private long version;

    public static AssignmentResponse fromEntity(CourierAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .parcelId(assignment.getParcelId())
                .courierId(assignment.getCourierId())
                .dispatcherId(assignment.getDispatcherId())
                .status(assignment.getStatus())
                .notes(assignment.getNotes())
                .assignedAt(assignment.getAssignedAt())
                .updatedAt(assignment.getUpdatedAt())
                .version(assignment.getVersion())
                .build();
    }
}
