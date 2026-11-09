package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.courier.entity.Courier;
import com.swiftdrop.backend.modules.courier.repository.CourierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourierService {

    private final CourierRepository courierRepository;

    public Optional<Courier> findByUserId(UUID userId) {
        return courierRepository.findByUserId(userId);
    }
}