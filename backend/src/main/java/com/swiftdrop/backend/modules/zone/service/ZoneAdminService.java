package com.swiftdrop.backend.modules.zone.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.pricing.exception.PricingException;
import com.swiftdrop.backend.modules.zone.dto.AdminZoneResponse;
import com.swiftdrop.backend.modules.zone.dto.CreateZoneRequest;
import com.swiftdrop.backend.modules.zone.dto.UpdateZoneRequest;
import com.swiftdrop.backend.modules.zone.entity.Zone;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

import lombok.RequiredArgsConstructor;

/**
 * Admin management of delivery zones. Zones are never deleted, because parcels keep
 * pointing at them; an admin deactivates a zone instead, which hides it from booking
 * and estimates straight away (nothing is cached).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ZoneAdminService {
    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<AdminZoneResponse> listAll() {
        return zoneRepository.findAllByOrderByNameAsc().stream().map(AdminZoneResponse::from).toList();
    }

    /** @throws PricingException 409 if the code is already used */
    public AdminZoneResponse create(CreateZoneRequest request) {
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (zoneRepository.existsByCode(code)) {
            throw PricingException.duplicateZoneCode(code);
        }
        try {
            Zone zone = zoneRepository.saveAndFlush(Zone.create(code, request.name().strip(), request.surcharge()));
            return AdminZoneResponse.from(zone);
        } catch (DataIntegrityViolationException ex) {
            // Another admin created the same code at the same moment (unique constraint).
            throw PricingException.duplicateZoneCode(code);
        }
    }

    /** @throws PricingException 404 if there is no such zone */
    public AdminZoneResponse update(UUID id, UpdateZoneRequest request) {
        Zone zone = find(id);
        zone.update(request.name().strip(), request.surcharge());
        return AdminZoneResponse.from(zoneRepository.saveAndFlush(zone));
    }

    /** @throws PricingException 404 if there is no such zone */
    public AdminZoneResponse setActive(UUID id, boolean active) {
        Zone zone = find(id);
        zone.setActive(active);
        return AdminZoneResponse.from(zoneRepository.saveAndFlush(zone));
    }

    private Zone find(UUID id) {
        return zoneRepository.findById(id).orElseThrow(() -> PricingException.zoneNotFound(id));
    }
}
