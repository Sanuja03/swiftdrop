package com.swiftdrop.backend.modules.parcel.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    List<Parcel> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<Parcel> findByMerchantIdAndStatusOrderByCreatedAtDesc(UUID merchantId, ParcelStatus status);

    Optional<Parcel> findByTrackingNumberAndMerchantId(String trackingNumber, UUID merchantId);

    long countByMerchantId(UUID merchantId);

    long countByMerchantIdAndStatus(UUID merchantId, ParcelStatus status);
}

