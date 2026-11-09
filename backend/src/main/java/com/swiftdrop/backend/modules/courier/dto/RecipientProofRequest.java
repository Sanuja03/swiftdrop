package com.swiftdrop.backend.modules.courier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipientProofRequest(

        @NotBlank(message = "Recipient name is required")
        @Size(
                max = 150,
                message = "Recipient name must not exceed 150 characters"
        )
        String recipientName

) {
}