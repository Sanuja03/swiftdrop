package com.swiftdrop.backend.modules.courier.dto;

import com.swiftdrop.backend.modules.courier.entity.DeliveryProof;

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

    public static DeliveryProofResponse from(DeliveryProof proof) {
        String photoKey = proof.getPhotoObjectKey();

        return new DeliveryProofResponse(
                proof.getId(),
                proof.getParcelId(),
                proof.getRecipientName(),
                photoKey != null && !photoKey.isBlank(),
                proof.getCreatedAt(),
                proof.getPreviousProofId(),
                proof.getChangeReason()
        );
    }
}