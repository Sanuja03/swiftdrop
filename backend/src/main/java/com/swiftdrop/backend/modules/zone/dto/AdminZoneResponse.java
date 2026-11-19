package com.swiftdrop.backend.modules.zone.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.backend.modules.zone.entity.Zone;

/** A zone as admins see it, including inactive ones. */
public record AdminZoneResponse(UUID id, String code, String name, BigDecimal surcharge, boolean active,
                                Instant updatedAt) {
    public static AdminZoneResponse from(Zone zone) {
        return new AdminZoneResponse(zone.getId(), zone.getCode(), zone.getName(), zone.getSurcharge(),
                zone.isActive(), zone.getUpdatedAt());
    }
}
