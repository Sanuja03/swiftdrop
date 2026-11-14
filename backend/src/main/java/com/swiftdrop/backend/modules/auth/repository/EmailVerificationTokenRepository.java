package com.swiftdrop.backend.modules.auth.repository;

import com.swiftdrop.backend.modules.auth.entity.EmailVerificationToken;
import com.swiftdrop.backend.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findFirstByTokenAndVerifiedFalseOrderByCreatedAtDesc(String token);

    Optional<EmailVerificationToken> findFirstByUserAndTokenAndVerifiedFalseOrderByCreatedAtDesc(User user, String token);

    void deleteByUser(User user);
}

