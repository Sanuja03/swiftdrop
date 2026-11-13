package com.swiftdrop.backend.modules.zone.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.swiftdrop.backend.modules.zone.entity.Zone;

public record ZoneResponse(UUID id, String code, String name, BigDecimal surcharge) {
    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getCode(), zone.getName(), zone.getSurcharge());
    }
}
