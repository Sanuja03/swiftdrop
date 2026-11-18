package com.swiftdrop.backend.modules.assistant.service;

import com.swiftdrop.backend.modules.assistant.dto.AiChatResponse;
import com.swiftdrop.backend.modules.assistant.dto.ChatMessageResponse;
import com.swiftdrop.backend.modules.assistant.dto.ChatTurnResponse;
import com.swiftdrop.backend.modules.assistant.dto.CreateSessionResponse;
import com.swiftdrop.backend.modules.assistant.dto.ToolExecutionRecord;
import com.swiftdrop.backend.modules.assistant.entity.ChatMessage;
import com.swiftdrop.backend.modules.assistant.entity.MessageSender;
import com.swiftdrop.backend.modules.assistant.exception.AiServiceException;
import com.swiftdrop.backend.modules.assistant.exception.SessionNotFoundException;
import com.swiftdrop.backend.modules.assistant.repository.ChatMessageRepository;
import com.swiftdrop.backend.modules.assistant.service.impl.AssistantChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantChatServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AiOrchestrationService aiOrchestrationService;

    @InjectMocks
    private AssistantChatServiceImpl assistantChatService;

    private UUID customerA;
    private UUID customerB;
    private String sessionId;

    @BeforeEach
    void setUp() {
        customerA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        customerB = UUID.fromString("22222222-2222-2222-2222-222222222222");
        sessionId = "session-12345678-abcd-ef01-2345-6789abcdef01";
    }

    @Test
    @DisplayName("Create session successfully generates sessionId and persists initial session marker")
    void createSession_success() {
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateSessionResponse response = assistantChatService.createSession(customerA);

        assertThat(response).isNotNull();
        assertThat(response.getSessionId()).startsWith("session-");
        assertThat(response.getCreatedAt()).isNotNull();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        ChatMessage saved = captor.getValue();
        assertThat(saved.getSessionId()).isEqualTo(response.getSessionId());
        assertThat(saved.getUserId()).isEqualTo(customerA);
        assertThat(saved.getSender()).isEqualTo(MessageSender.SYSTEM);
        assertThat(saved.getContent()).isEqualTo("Session initialized");
    }

    @Test
    @DisplayName("Send message successfully orchestrates turn and persists user and assistant messages")
    void sendMessage_success() {
        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(new ArrayList<>(List.of(sessionInit)));

        AiChatResponse aiResponse = AiChatResponse.builder()
                .answer("Your parcel SDP-1001 is currently in transit.")
                .executedTools(Collections.emptyList())
                .build();

        when(aiOrchestrationService.processCustomerMessage(eq("Where is my parcel?"), eq(customerA), any()))
                .thenReturn(aiResponse);

        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatTurnResponse response = assistantChatService.sendMessage(sessionId, "Where is my parcel?", customerA);

        assertThat(response).isNotNull();
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getAnswer()).isEqualTo("Your parcel SDP-1001 is currently in transit.");
        assertThat(response.getExecutedTools()).isEmpty();

        // Verify two messages were saved: user turn, then assistant turn
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        List<ChatMessage> savedMessages = captor.getAllValues();

        ChatMessage userSaved = savedMessages.get(0);
        assertThat(userSaved.getSessionId()).isEqualTo(sessionId);
        assertThat(userSaved.getUserId()).isEqualTo(customerA);
        assertThat(userSaved.getSender()).isEqualTo(MessageSender.USER);
        assertThat(userSaved.getContent()).isEqualTo("Where is my parcel?");

        ChatMessage assistantSaved = savedMessages.get(1);
        assertThat(assistantSaved.getSessionId()).isEqualTo(sessionId);
        assertThat(assistantSaved.getUserId()).isEqualTo(customerA);
        assertThat(assistantSaved.getSender()).isEqualTo(MessageSender.ASSISTANT);
        assertThat(assistantSaved.getContent()).isEqualTo("Your parcel SDP-1001 is currently in transit.");
    }

    @Test
    @DisplayName("Send message records executed tools on assistant message and response")
    void sendMessage_withExecutedTools() {
        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(new ArrayList<>(List.of(sessionInit)));

        ToolExecutionRecord toolRecord = ToolExecutionRecord.builder()
                .toolCallId("call_123")
                .toolName("track_parcel")
                .toolArguments("{\"trackingNumber\":\"SDP-1001\"}")
                .toolResult("{\"trackingNumber\":\"SDP-1001\",\"status\":\"IN_TRANSIT\"}")
                .success(true)
                .build();

        AiChatResponse aiResponse = AiChatResponse.builder()
                .answer("Parcel SDP-1001 is in transit.")
                .executedTools(List.of(toolRecord))
                .build();

        when(aiOrchestrationService.processCustomerMessage(any(), eq(customerA), any()))
                .thenReturn(aiResponse);

        ChatTurnResponse response = assistantChatService.sendMessage(sessionId, "Check SDP-1001", customerA);

        assertThat(response.getExecutedTools()).hasSize(1);
        assertThat(response.getExecutedTools().get(0).getToolName()).isEqualTo("track_parcel");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        ChatMessage assistantSaved = captor.getAllValues().get(1);
        assertThat(assistantSaved.getToolName()).isEqualTo("track_parcel");
    }

    @Test
    @DisplayName("Send message rejects nonexistent session with SessionNotFoundException")
    void sendMessage_nonexistentSession() {
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> assistantChatService.sendMessage(sessionId, "Hello", customerA))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("Chat session not found");

        verify(chatMessageRepository, never()).save(any());
        verifyNoInteractions(aiOrchestrationService);
    }

    @Test
    @DisplayName("Customer A cannot send message to Customer B's session (AccessDeniedException)")
    void sendMessage_crossCustomerIsolation() {
        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerB) // Owned by Customer B
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(sessionInit));

        // Customer A attempts to access Customer B's session
        assertThatThrownBy(() -> assistantChatService.sendMessage(sessionId, "Hacked", customerA))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to access chat session");

        verify(chatMessageRepository, never()).save(any());
        verifyNoInteractions(aiOrchestrationService);
    }

    @Test
    @DisplayName("Empty or whitespace message is rejected")
    void sendMessage_emptyMessage() {
        assertThatThrownBy(() -> assistantChatService.sendMessage(sessionId, "   ", customerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message cannot be empty");

        assertThatThrownBy(() -> assistantChatService.sendMessage(sessionId, "", customerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message cannot be empty");
    }

    @Test
    @DisplayName("Excessively long message (> 1000 characters) is rejected")
    void sendMessage_longMessage() {
        String longMessage = "a".repeat(1001);
        assertThatThrownBy(() -> assistantChatService.sendMessage(sessionId, longMessage, customerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 1000 characters");
    }

    @Test
    @DisplayName("Invalid session ID format is rejected")
    void sendMessage_invalidSessionId() {
        assertThatThrownBy(() -> assistantChatService.sendMessage("bad/session/id", "Hello", customerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid session identifier");
    }

    @Test
    @DisplayName("AI provider failure persists user message first and throws AiServiceException without fabricating answer")
    void sendMessage_providerFailurePreservesUserMessage() {
        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(new ArrayList<>(List.of(sessionInit)));

        when(aiOrchestrationService.processCustomerMessage(any(), eq(customerA), any()))
                .thenThrow(new AiServiceException("Upstream LLM gateway timed out"));

        assertThatThrownBy(() -> assistantChatService.sendMessage(sessionId, "Where is my parcel?", customerA))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Upstream LLM gateway timed out");

        // Verify user message was persisted, but assistant message was NOT persisted
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(1)).save(captor.capture());
        ChatMessage saved = captor.getValue();
        assertThat(saved.getSender()).isEqualTo(MessageSender.USER);
        assertThat(saved.getContent()).isEqualTo("Where is my parcel?");
    }

    @Test
    @DisplayName("Prior conversation turns do NOT duplicate current user message in AI history")
    void sendMessage_noDuplicateCurrentUserMessageInAiHistory() {
        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(Instant.now().minusSeconds(120))
                .build();

        ChatMessage priorUser = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.USER)
                .content("Prior question")
                .createdAt(Instant.now().minusSeconds(100))
                .build();

        ChatMessage priorAssistant = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.ASSISTANT)
                .content("Prior answer")
                .createdAt(Instant.now().minusSeconds(90))
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(sessionInit, priorUser, priorAssistant));

        when(aiOrchestrationService.processCustomerMessage(eq("New question"), eq(customerA), any()))
                .thenReturn(AiChatResponse.builder().answer("New answer").build());

        assistantChatService.sendMessage(sessionId, "New question", customerA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiOrchestrationService).processCustomerMessage(eq("New question"), eq(customerA), historyCaptor.capture());

        List<ChatMessage> passedHistory = historyCaptor.getValue();
        assertThat(passedHistory).hasSize(2);
        assertThat(passedHistory.get(0).getContent()).isEqualTo("Prior question");
        assertThat(passedHistory.get(1).getContent()).isEqualTo("Prior answer");
        // Verify current question is not in passed prior history
        assertThat(passedHistory).noneMatch(m -> m.getContent().equals("New question"));
    }

    @Test
    @DisplayName("Retrieve conversation history returns user and assistant turns in ascending chronological order")
    void getSessionHistory_success() {
        Instant t0 = Instant.now().minusSeconds(180);
        Instant t1 = Instant.now().minusSeconds(120);
        Instant t2 = Instant.now().minusSeconds(60);

        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(t0)
                .build();

        ChatMessage userTurn = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.USER)
                .content("Hello assistant")
                .createdAt(t1)
                .build();

        ChatMessage assistantTurn = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .userId(customerA)
                .sender(MessageSender.ASSISTANT)
                .content("Hello customer")
                .createdAt(t2)
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(sessionInit, userTurn, assistantTurn));

        List<ChatMessageResponse> history = assistantChatService.getSessionHistory(sessionId, customerA);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getSender()).isEqualTo(MessageSender.USER);
        assertThat(history.get(0).getContent()).isEqualTo("Hello assistant");
        assertThat(history.get(0).getCreatedAt()).isEqualTo(t1);

        assertThat(history.get(1).getSender()).isEqualTo(MessageSender.ASSISTANT);
        assertThat(history.get(1).getContent()).isEqualTo("Hello customer");
        assertThat(history.get(1).getCreatedAt()).isEqualTo(t2);
    }

    @Test
    @DisplayName("Customer A cannot retrieve Customer B's session history (AccessDeniedException)")
    void getSessionHistory_crossCustomerIsolation() {
        ChatMessage sessionInit = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(customerB)
                .sender(MessageSender.SYSTEM)
                .content("Session initialized")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(sessionInit));

        assertThatThrownBy(() -> assistantChatService.getSessionHistory(sessionId, customerA))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to access chat session");
    }

    @Test
    @DisplayName("Retrieve history for nonexistent session throws SessionNotFoundException")
    void getSessionHistory_nonexistentSession() {
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> assistantChatService.getSessionHistory(sessionId, customerA))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("Chat session not found");
    }

    @Test
    @DisplayName("Retrieve history with invalid session ID format throws IllegalArgumentException")
    void getSessionHistory_invalidSessionId() {
        assertThatThrownBy(() -> assistantChatService.getSessionHistory("invalid/id!", customerA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid session identifier");
    }
}
