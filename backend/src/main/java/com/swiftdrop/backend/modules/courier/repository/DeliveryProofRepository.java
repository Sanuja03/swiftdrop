package com.swiftdrop.backend.modules.courier.repository;

import com.swiftdrop.backend.modules.courier.entity.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryProofRepository
        extends JpaRepository<DeliveryProof, UUID> {

    boolean existsByParcelId(UUID parcelId);

    List<DeliveryProof> findByParcelIdOrderByCreatedAtDesc(UUID parcelId);
}