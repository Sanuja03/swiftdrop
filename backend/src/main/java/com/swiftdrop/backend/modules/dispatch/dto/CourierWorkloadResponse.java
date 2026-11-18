package com.swiftdrop.backend.modules.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierWorkloadResponse {

    private UUID courierId;
    private String courierName;
    private String courierEmail;
    private String courierPhone;
    private long activeAssignmentsCount;
    private String workloadStatus;
}
