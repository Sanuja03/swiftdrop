package com.swiftdrop.backend.modules.assistant.service;

import com.swiftdrop.backend.modules.assistant.dto.AiChatResponse;
import com.swiftdrop.backend.modules.assistant.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates Generative AI model interactions, tool definitions, tool-calling loops,
 * and context management for the SwiftDrop Delivery Assistant.
 */
public interface AiOrchestrationService {

    /**
     * Orchestrates a natural-language conversation turn with the Generative AI provider,
     * executing tools when requested, and returning the grounded final response.
     *
     * @param userMessage            the customer's message
     * @param authenticatedCustomerId authenticated customer ID (or null for guest)
     * @param conversationHistory    prior turns in this session
     * @return AI assistant turn response with final answer and executed tools audit
     */
    AiChatResponse processCustomerMessage(
            String userMessage,
            UUID authenticatedCustomerId,
            List<ChatMessage> conversationHistory
    );
}
