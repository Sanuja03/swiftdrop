package com.swiftdrop.backend.modules.zone.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.zone.dto.ZoneResponse;
import com.swiftdrop.backend.modules.zone.service.ZoneService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class ZoneController {
    private final ZoneService zoneService;

    @GetMapping
    public List<ZoneResponse> getActiveZones() {
        return zoneService.getActiveZones();
    }
}
