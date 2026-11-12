package com.swiftdrop.backend.modules.zone.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.zone.dto.ZoneResponse;
import com.swiftdrop.backend.modules.zone.repository.ZoneRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneService {
    private final ZoneRepository zoneRepository;

    public List<ZoneResponse> getActiveZones() {
        return zoneRepository.findAllByActiveTrueOrderByNameAsc().stream().map(ZoneResponse::from).toList();
    }
}
