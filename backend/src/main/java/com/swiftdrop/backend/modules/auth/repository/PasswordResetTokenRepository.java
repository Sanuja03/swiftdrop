package com.swiftdrop.backend.modules.auth.repository;

import com.swiftdrop.backend.modules.auth.entity.PasswordResetToken;
import com.swiftdrop.backend.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findFirstByTokenAndUsedFalseOrderByCreatedAtDesc(String token);

    void deleteByUser(User user);
}

