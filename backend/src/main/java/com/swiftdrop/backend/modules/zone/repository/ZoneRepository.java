package com.swiftdrop.backend.modules.zone.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.backend.modules.zone.entity.Zone;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    List<Zone> findAllByActiveTrueOrderByNameAsc();
    Optional<Zone> findByIdAndActiveTrue(UUID id);
}
