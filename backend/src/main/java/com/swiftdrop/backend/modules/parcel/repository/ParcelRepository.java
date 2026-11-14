package com.swiftdrop.backend.modules.parcel.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    List<Parcel> findByStatusOrderByCreatedAtDesc(ParcelStatus status);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}

