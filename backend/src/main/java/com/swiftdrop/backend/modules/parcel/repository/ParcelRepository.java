package com.swiftdrop.backend.modules.parcel.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    List<Parcel> findByStatusOrderByCreatedAtDesc(ParcelStatus status);

    /** A customer's parcels, newest booking first. */
    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /** Locks the parcel row so two couriers can't update it at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> lockById(UUID id);

    // Merchant API
    List<Parcel> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<Parcel> findByMerchantIdAndStatusOrderByCreatedAtDesc(UUID merchantId, ParcelStatus status);

    Optional<Parcel> findByTrackingNumberAndMerchantId(String trackingNumber, UUID merchantId);

    long countByMerchantId(UUID merchantId);

    long countByMerchantIdAndStatus(UUID merchantId, ParcelStatus status);
}