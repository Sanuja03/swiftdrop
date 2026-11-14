package com.swiftdrop.backend.modules.assistant.repository;

import com.swiftdrop.backend.modules.assistant.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countBySessionId(String sessionId);

    Optional<ChatMessage> findFirstBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessage> findBySessionIdAndUserIdOrderByCreatedAtAsc(String sessionId, UUID userId);

    boolean existsBySessionIdAndUserId(String sessionId, UUID userId);
}
