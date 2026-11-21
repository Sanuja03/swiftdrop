package com.swiftdrop.backend.modules.zone.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.zone.entity.Zone;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    // Booking and public zone list
    List<Zone> findAllByActiveTrueOrderByNameAsc();

    Optional<Zone> findByIdAndActiveTrue(UUID id);

    // Admin zone management
    List<Zone> findAllByOrderByNameAsc();

    boolean existsByCode(String code);

    long countByActiveTrue();

    // Merchant API: integrations that send a city/zone name or code instead of a zone id
    Optional<Zone> findFirstByNameIgnoreCaseAndActiveTrue(String name);

    Optional<Zone> findFirstByCodeIgnoreCaseAndActiveTrue(String code);
}