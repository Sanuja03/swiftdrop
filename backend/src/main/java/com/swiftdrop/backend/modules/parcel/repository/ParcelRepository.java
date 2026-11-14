package com.swiftdrop.backend.modules.parcel.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import com.swiftdrop.backend.modules.parcel.entity.Parcel;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> lockById(UUID id);
}
