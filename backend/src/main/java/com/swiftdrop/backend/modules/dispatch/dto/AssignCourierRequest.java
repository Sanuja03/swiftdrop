package com.swiftdrop.backend.modules.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignCourierRequest {

    @NotNull(message = "Parcel ID is required")
    private UUID parcelId;

    @NotNull(message = "Courier ID is required")
    private UUID courierId;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
