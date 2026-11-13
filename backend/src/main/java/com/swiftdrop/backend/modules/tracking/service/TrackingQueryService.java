package com.swiftdrop.backend.modules.tracking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.parcel.service.ParcelLookupService;
import com.swiftdrop.backend.modules.tracking.dto.PublicTrackingResponse;
import com.swiftdrop.backend.modules.tracking.repository.ParcelStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read side of tracking: builds what the tracking pages show.
 */
@Service
@RequiredArgsConstructor
public class TrackingQueryService {

    private final ParcelLookupService parcelLookupService;
    private final ParcelStatusHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public PublicTrackingResponse getPublicTracking(String trackingNumber) {
        Parcel parcel = parcelLookupService.getByTrackingNumber(trackingNumber);
        return PublicTrackingResponse.from(parcel, historyRepository.findByParcel_IdOrderByChangedAtAsc(parcel.getId()));
    }
}
