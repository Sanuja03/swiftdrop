package com.swiftdrop.backend.modules.receipt.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.receipt.entity.Receipt;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByParcel_Id(UUID parcelId);
}
