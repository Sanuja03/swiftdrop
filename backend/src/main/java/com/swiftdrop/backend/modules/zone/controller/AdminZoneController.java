package com.swiftdrop.backend.modules.zone.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.zone.dto.AdminZoneResponse;
import com.swiftdrop.backend.modules.zone.dto.CreateZoneRequest;
import com.swiftdrop.backend.modules.zone.dto.UpdateZoneRequest;
import com.swiftdrop.backend.modules.zone.dto.ZoneActiveRequest;
import com.swiftdrop.backend.modules.zone.service.ZoneAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Zone management. ROLE_ADMIN only (SecurityConfig: /api/v1/admin/**). */
@RestController
@RequestMapping(AdminZoneController.BASE_PATH)
@RequiredArgsConstructor
public class AdminZoneController {
    public static final String BASE_PATH = "/api/v1/admin/zones";

    private final ZoneAdminService zoneAdminService;

    @GetMapping
    public List<AdminZoneResponse> listAll() {
        return zoneAdminService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminZoneResponse create(@Valid @RequestBody CreateZoneRequest request) {
        return zoneAdminService.create(request);
    }

    @PutMapping("/{id}")
    public AdminZoneResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateZoneRequest request) {
        return zoneAdminService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminZoneResponse setActive(@PathVariable UUID id, @Valid @RequestBody ZoneActiveRequest request) {
        return zoneAdminService.setActive(id, request.active());
    }
}
