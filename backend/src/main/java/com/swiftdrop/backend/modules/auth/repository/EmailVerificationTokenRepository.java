package com.swiftdrop.backend.modules.auth.repository;

import com.swiftdrop.backend.modules.auth.entity.EmailVerificationToken;
import com.swiftdrop.backend.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findFirstByTokenAndVerifiedFalseOrderByCreatedAtDesc(String token);

    Optional<EmailVerificationToken> findFirstByUserAndTokenAndVerifiedFalseOrderByCreatedAtDesc(User user, String token);

    void deleteByUser(User user);
}

