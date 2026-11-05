package com.swiftdrop.backend.modules.parcel.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);
}
