package com.swiftdrop.backend.modules.courier.service;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.courier.entity.Courier;
import com.swiftdrop.backend.modules.courier.repository.CourierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourierService {

    private final CourierRepository courierRepository;
    private final UserRepository userRepository;

    public Optional<Courier> findByUserId(UUID userId) {
        return courierRepository.findByUserId(userId);
    }

    public Courier requireCurrentCourier(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Courier login is required");
        }

        User user = userRepository.findByEmail(principal.getName())
                .filter(account -> account.getRole() == Role.ROLE_COURIER)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() ->
                        new AccessDeniedException("Active courier account is required"));

        return findByUserId(user.getId())
                .orElseThrow(() ->
                        new AccessDeniedException("Courier profile is not registered"));
    }
}