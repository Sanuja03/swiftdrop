package com.swiftdrop.backend.modules.dispatch.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectAssignmentRequest {

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
