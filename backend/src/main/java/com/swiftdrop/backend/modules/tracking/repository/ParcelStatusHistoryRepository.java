package com.swiftdrop.backend.modules.tracking.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.tracking.entity.ParcelStatusHistory;

public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {

    List<ParcelStatusHistory> findByParcel_IdOrderByChangedAtAsc(UUID parcelId);
}
