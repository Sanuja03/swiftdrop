package com.swiftdrop.backend.modules.courier.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryProofResponse(
        UUID id,
        UUID parcelId,
        String recipientName,
        boolean hasPhoto,
        Instant createdAt,
        UUID previousProofId,
        String changeReason
) {
}