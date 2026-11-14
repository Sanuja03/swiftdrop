package com.swiftdrop.backend.modules.courier.repository;

import com.swiftdrop.backend.modules.courier.entity.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryProofRepository
        extends JpaRepository<DeliveryProof, UUID> {

    boolean existsByParcelId(UUID parcelId);

    Optional<DeliveryProof> findByIdAndParcelId(UUID id, UUID parcelId);

    @Query("""
            select p from DeliveryProof p where p.parcelId = :parcelId
            and not exists (select next.id from DeliveryProof next where next.previousProofId = p.id)
            """)
    Optional<DeliveryProof> findCurrentByParcelId(UUID parcelId);

    List<DeliveryProof> findByParcelIdOrderByCreatedAtDesc(UUID parcelId);
}
