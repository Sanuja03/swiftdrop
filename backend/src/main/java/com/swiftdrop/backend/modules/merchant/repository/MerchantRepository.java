package com.swiftdrop.backend.modules.merchant.repository;

import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    /** Serialize configuration/rotation without a second entity mapping or schema change. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Merchant m where m.userId = :userId")
    Optional<Merchant> findForWebhookUpdate(@org.springframework.data.repository.query.Param("userId") UUID userId);

    Optional<Merchant> findByApiKey(String apiKey);

    boolean existsByApiKey(String apiKey);

    boolean existsByUserId(UUID userId);

    List<Merchant> findByRegistrationStatus(MerchantRegistrationStatus status);
}

