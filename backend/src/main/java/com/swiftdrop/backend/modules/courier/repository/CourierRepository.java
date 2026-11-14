package com.swiftdrop.backend.modules.courier.repository;

import com.swiftdrop.backend.modules.courier.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourierRepository
        extends JpaRepository<Courier, UUID> {

    Optional<Courier> findByUserId(UUID userId);
}