package com.swiftdrop.backend.modules.assistant.service.impl;

import com.swiftdrop.backend.modules.assistant.dto.AiChatResponse;
import com.swiftdrop.backend.modules.assistant.dto.ChatMessageResponse;
import com.swiftdrop.backend.modules.assistant.dto.ChatTurnResponse;
import com.swiftdrop.backend.modules.assistant.dto.CreateSessionResponse;
import com.swiftdrop.backend.modules.assistant.dto.ToolExecutionRecord;
import com.swiftdrop.backend.modules.assistant.entity.ChatMessage;
import com.swiftdrop.backend.modules.assistant.entity.MessageSender;
import com.swiftdrop.backend.modules.assistant.exception.SessionNotFoundException;
import com.swiftdrop.backend.modules.assistant.repository.ChatMessageRepository;
import com.swiftdrop.backend.modules.assistant.service.AiOrchestrationService;
import com.swiftdrop.backend.modules.assistant.service.AssistantChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatServiceImpl implements AssistantChatService {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ChatMessageRepository chatMessageRepository;
    private final AiOrchestrationService aiOrchestrationService;

    @Override
    public CreateSessionResponse createSession(UUID customerId) {
        Objects.requireNonNull(customerId, "Customer ID must not be null");

        String sessionId = "session-" + UUID.randomUUID();
        Instant now = Instant.now();

        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerId)
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(now)
                .build();

        chatMessageRepository.save(sessionInit);
        log.info("Created chat session [{}] for customer [{}]", sessionId, customerId);

        return CreateSessionResponse.builder()
                .sessionId(sessionId)
                .createdAt(now)
                .build();
    }

    @Override
    public ChatTurnResponse sendMessage(String sessionId, String message, UUID customerId) {
        validateSessionId(sessionId);
        validateMessage(message);
        Objects.requireNonNull(customerId, "Customer ID must not be null");

        List<ChatMessage> existingMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (existingMessages.isEmpty()) {
            throw new SessionNotFoundException("Chat session not found: " + sessionId);
        }

        UUID ownerId = existingMessages.get(0).getUserId();
        if (ownerId == null || !ownerId.equals(customerId)) {
            log.warn("Access denied: user [{}] attempted to access session [{}] owned by [{}]",
                    customerId, sessionId, ownerId);
            throw new AccessDeniedException("You do not have permission to access chat session: " + sessionId);
        }

        // Prior conversation turns for AI context (excluding system markers)
        List<ChatMessage> priorTurns = existingMessages.stream()
                .filter(m -> m.getSender() == MessageSender.USER || m.getSender() == MessageSender.ASSISTANT)
                .toList();

        // Persist user message first (committed immediately)
        Instant userCreatedAt = Instant.now();
        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerId)
                .sender(MessageSender.USER)
                .content(message.trim())
                .createdAt(userCreatedAt)
                .build();

        chatMessageRepository.save(userMessage);

        // Orchestrate AI turn OUTSIDE of database transaction
        AiChatResponse aiResponse = aiOrchestrationService.processCustomerMessage(
                message.trim(),
                customerId,
                priorTurns
        );

        // Determine tool summary if tools were executed
        String toolSummary = null;
        if (aiResponse.getExecutedTools() != null && !aiResponse.getExecutedTools().isEmpty()) {
            toolSummary = aiResponse.getExecutedTools().stream()
                    .map(ToolExecutionRecord::getToolName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);
            if (toolSummary != null && toolSummary.length() > 50) {
                toolSummary = toolSummary.substring(0, 50);
            }
        }

        // Persist assistant final response
        Instant assistantCreatedAt = Instant.now();
        ChatMessage assistantMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerId)
                .sender(MessageSender.ASSISTANT)
                .content(aiResponse.getAnswer())
                .toolName(toolSummary)
                .createdAt(assistantCreatedAt)
                .build();

        chatMessageRepository.save(assistantMessage);

        return ChatTurnResponse.builder()
                .sessionId(sessionId)
                .answer(aiResponse.getAnswer())
                .executedTools(aiResponse.getExecutedTools())
                .createdAt(assistantCreatedAt)
                .build();
    }

    @Override
    public List<ChatMessageResponse> getSessionHistory(String sessionId, UUID customerId) {
        validateSessionId(sessionId);
        Objects.requireNonNull(customerId, "Customer ID must not be null");

        List<ChatMessage> existingMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (existingMessages.isEmpty()) {
            throw new SessionNotFoundException("Chat session not found: " + sessionId);
        }

        UUID ownerId = existingMessages.get(0).getUserId();
        if (ownerId == null || !ownerId.equals(customerId)) {
            log.warn("Access denied: user [{}] attempted to access history for session [{}] owned by [{}]",
                    customerId, sessionId, ownerId);
            throw new AccessDeniedException("You do not have permission to access chat session: " + sessionId);
        }

        return existingMessages.stream()
                .filter(m -> m.getSender() != MessageSender.SYSTEM)
                .map(this::toResponse)
                .toList();
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("Invalid session identifier: " + sessionId);
        }
    }

    private void validateMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .sender(message.getSender())
                .content(message.getContent())
                .toolName(message.getToolName())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
