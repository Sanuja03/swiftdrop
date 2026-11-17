package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.courier.dto.DeliveryProofResponse;
import com.swiftdrop.backend.modules.courier.entity.DeliveryProof;
import com.swiftdrop.backend.modules.courier.repository.DeliveryProofRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryProofService {

    private final CourierService couriers;
    private final CourierDeliveryService deliveries;
    private final ParcelRepository parcels;
    private final DeliveryProofRepository proofs;
    private final ProofInputValidator validator;
    private final ProofPhotoStorage photos;
    private final Clock clock;

    @Transactional
    public DeliveryProofResponse save(UUID parcelId, Principal principal, String recipientName,
                                      MultipartFile photo, String changeReason) {
        var courier = couriers.requireCurrentCourier(principal);
        boolean correction = changeReason != null;
        String reason = correction ? validator.validateChangeReason(changeReason) : null;
        String name = photo == null || recipientName != null ? validator.validateRecipientName(recipientName) : null;

        var parcel = parcels.lockById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!correction || parcel.getStatus() != ParcelStatus.DELIVERED) {
            deliveries.lockAssignment(parcelId, courier);
        }
        var previous = proofs.findCurrentByParcelId(parcelId).orElse(null);

        if (correction) {
            if (previous == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proof not found");
            }
            if (parcel.getStatus() == ParcelStatus.DELIVERED && !courier.getId().equals(previous.getCourierId())) {
                throw new AccessDeniedException("Only the courier who provided the proof can correct it");
            }
            if (parcel.getStatus() != ParcelStatus.DELIVERED && parcel.getStatus() != ParcelStatus.OUT_FOR_DELIVERY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Proof cannot be corrected at this status");
            }
        } else {
            if (parcel.getStatus() != ParcelStatus.OUT_FOR_DELIVERY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel must be out for delivery to submit proof");
            }
            if (previous != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A proof already exists. Use the correction endpoint");
            }
        }

        String photoKey = photo == null ? null : photos.upload(parcelId, photo);
        boolean sameCourier = previous != null && courier.getId().equals(previous.getCourierId());
        if (sameCourier && photo == null) {
            photoKey = previous.getPhotoObjectKey();
        }
        if (sameCourier && name == null) {
            name = previous.getRecipientName();
        }
        UUID previousId = previous == null ? null : previous.getId();
        var saved = proofs.save(new DeliveryProof(parcelId, courier.getId(), name, photoKey,
                clock.instant(), previousId, reason));
        return DeliveryProofResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DeliveryProofResponse> history(UUID parcelId) {
        return proofs.findByParcelIdOrderByCreatedAtDesc(parcelId).stream()
                .map(DeliveryProofResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InputStream photo(UUID parcelId, UUID proofId) {
        String key = proofs.findByIdAndParcelId(proofId, parcelId)
                .map(DeliveryProof::getPhotoObjectKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proof photo not found"));
        return photos.download(key);
    }
}
