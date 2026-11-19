package com.swiftdrop.backend.modules.auth.repository;

import com.swiftdrop.backend.modules.auth.entity.RefreshToken;
import com.swiftdrop.backend.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);
}

