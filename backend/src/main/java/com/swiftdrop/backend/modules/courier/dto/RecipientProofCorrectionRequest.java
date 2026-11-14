package com.swiftdrop.backend.modules.courier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipientProofCorrectionRequest(

        @NotBlank(message = "Recipient name is required")
        @Size(max = 150)
        String recipientName,

        @NotBlank(message = "Change reason is required")
        @Size(max = 500)
        String changeReason

) {
}