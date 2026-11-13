package com.swiftdrop.backend.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.tracking.service.ParcelStatusService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Local development only: changes a parcel's status the same way the courier app will,
 * so live tracking can be tried before the courier module exists.
 */
@Profile(DevProfile.NAME)
@RestController
@RequestMapping(DevParcelStatusController.BASE_PATH)
@RequiredArgsConstructor
public class DevParcelStatusController {

    static final String BASE_PATH = "/api/v1/dev/parcels";

    private final ParcelLookupService parcelLookupService;
    private final ParcelStatusService parcelStatusService;

    public record StatusChangeRequest(@NotNull ParcelStatus status, @Size(max = 500) String note) {
    }

    @PostMapping("/{trackingNumber}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable String trackingNumber,
                                             @Valid @RequestBody StatusChangeRequest request) {
        var parcel = parcelLookupService.getByTrackingNumber(trackingNumber);
        parcelStatusService.updateStatus(parcel.getId(), request.status(), request.note(), null);
        return ResponseEntity.noContent().build();
    }
}
