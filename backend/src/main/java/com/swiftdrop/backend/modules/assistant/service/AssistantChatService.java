package com.swiftdrop.backend.modules.assistant.service;

import com.swiftdrop.backend.modules.assistant.dto.ChatMessageResponse;
import com.swiftdrop.backend.modules.assistant.dto.ChatTurnResponse;
import com.swiftdrop.backend.modules.assistant.dto.CreateSessionResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service managing customer chat sessions, conversation persistence,
 * session ownership verification, and Generative AI turn orchestration.
 */
public interface AssistantChatService {

    /**
     * Starts a new chat session bound to the authenticated customer.
     *
     * @param customerId authenticated customer ID
     * @return session creation response containing the generated sessionId and timestamp
     */
    CreateSessionResponse createSession(UUID customerId);

    /**
     * Sends a customer message to an existing session, persists the user turn,
     * orchestrates the Generative AI answer (and tool calls) outside the DB transaction,
     * persists the assistant turn, and returns the response.
     *
     * @param sessionId  chat session identifier
     * @param message    customer message text
     * @param customerId authenticated customer ID
     * @return assistant turn response with answer and executed tools audit
     */
    ChatTurnResponse sendMessage(String sessionId, String message, UUID customerId);

    /**
     * Retrieves the conversation history for a session in chronological order,
     * verifying that the session belongs to the requesting customer.
     *
     * @param sessionId  chat session identifier
     * @param customerId authenticated customer ID
     * @return list of chat message responses in ascending chronological order
     */
    List<ChatMessageResponse> getSessionHistory(String sessionId, UUID customerId);
}
