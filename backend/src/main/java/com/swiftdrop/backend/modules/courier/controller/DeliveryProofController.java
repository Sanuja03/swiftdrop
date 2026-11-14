package com.swiftdrop.backend.modules.courier.controller;

import com.swiftdrop.backend.modules.courier.dto.DeliveryProofResponse;
import com.swiftdrop.backend.modules.courier.dto.RecipientProofCorrectionRequest;
import com.swiftdrop.backend.modules.courier.dto.RecipientProofRequest;
import com.swiftdrop.backend.modules.courier.service.DeliveryProofService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DeliveryProofController {

    private static final String COURIER = "/api/v1/courier/parcels/{parcelId}/proof";
    private static final String ADMIN = "/api/v1/admin/parcels/{parcelId}/proofs";

    private final DeliveryProofService proofs;

    @PostMapping(path = COURIER, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryProofResponse submitName(@PathVariable UUID parcelId, Principal principal,
                                            @Valid @RequestBody RecipientProofRequest request) {
        return proofs.save(parcelId, principal, request.recipientName(), null, null);
    }

    @PostMapping(path = COURIER + "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryProofResponse submitPhoto(@PathVariable UUID parcelId, Principal principal,
                                             @RequestParam MultipartFile photo) {
        return proofs.save(parcelId, principal, null, photo, null);
    }

    @PostMapping(path = COURIER + "/corrections", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryProofResponse correctName(@PathVariable UUID parcelId, Principal principal,
                                             @Valid @RequestBody RecipientProofCorrectionRequest request) {
        return proofs.save(parcelId, principal, request.recipientName(), null, request.changeReason());
    }

    @PostMapping(path = COURIER + "/corrections/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryProofResponse correctPhoto(@PathVariable UUID parcelId, Principal principal,
                                              @RequestParam MultipartFile photo,
                                              @RequestParam String changeReason) {
        return proofs.save(parcelId, principal, null, photo, changeReason);
    }

    @GetMapping(ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    public List<DeliveryProofResponse> history(@PathVariable UUID parcelId) {
        return proofs.history(parcelId);
    }

    @GetMapping(ADMIN + "/{proofId}/photo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InputStreamResource> photo(@PathVariable UUID parcelId, @PathVariable UUID proofId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(proofs.photo(parcelId, proofId)));
    }
}
