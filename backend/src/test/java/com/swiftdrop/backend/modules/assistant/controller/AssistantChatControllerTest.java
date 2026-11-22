package com.swiftdrop.backend.modules.assistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.assistant.dto.ChatMessageResponse;
import com.swiftdrop.backend.modules.assistant.dto.ChatTurnResponse;
import com.swiftdrop.backend.modules.assistant.dto.CreateSessionResponse;
import com.swiftdrop.backend.modules.assistant.dto.SendMessageRequest;
import com.swiftdrop.backend.modules.assistant.dto.ToolExecutionRecord;
import com.swiftdrop.backend.modules.assistant.entity.MessageSender;
import com.swiftdrop.backend.modules.assistant.exception.AiServiceException;
import com.swiftdrop.backend.modules.assistant.exception.AssistantExceptionHandler;
import com.swiftdrop.backend.modules.assistant.exception.SessionNotFoundException;
import com.swiftdrop.backend.modules.assistant.service.AssistantChatService;
import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.exception.GlobalExceptionHandler;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.auth.security.CustomAccessDeniedHandler;
import com.swiftdrop.backend.modules.auth.security.CustomAuthenticationEntryPoint;
import com.swiftdrop.backend.modules.auth.security.JwtAuthenticationFilter;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.auth.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistantChatController.class)
@Import({
        SecurityConfig.class,
        CustomAccessDeniedHandler.class,
        CustomAuthenticationEntryPoint.class,
        JwtAuthenticationFilter.class,
        AssistantExceptionHandler.class,
        GlobalExceptionHandler.class
})
class AssistantChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private AssistantChatService assistantChatService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String CUSTOMER_EMAIL = "customer@swiftdrop.lk";
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SESSION_ID = "session-12345678-abcd-ef01-2345-6789abcdef01";

    private User activeCustomer;

    @BeforeEach
    void setUp() {
        activeCustomer = User.builder()
                .id(CUSTOMER_ID)
                .email(CUSTOMER_EMAIL)
                .fullName("Test Customer")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Create session succeeds for authenticated customer returning 201 Created")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void createSession_authenticatedCustomer_returns201() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));
        when(assistantChatService.createSession(CUSTOMER_ID))
                .thenReturn(CreateSessionResponse.builder()
                        .sessionId(SESSION_ID)
                        .createdAt(Instant.parse("2026-11-14T10:00:00Z"))
                        .build());

        mockMvc.perform(post("/api/v1/assistant/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.createdAt").value("2026-11-14T10:00:00Z"));
    }

    @Test
    @DisplayName("Unauthenticated request to create session returns 401 Unauthorized")
    void createSession_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-customer role attempting to create session returns 403 Forbidden")
    @WithMockUser(username = "courier@swiftdrop.lk", roles = "COURIER")
    void createSession_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Inactive customer attempting to create session returns 403 Forbidden")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void createSession_inactiveCustomer_returns403() throws Exception {
        User inactiveUser = User.builder()
                .id(CUSTOMER_ID)
                .email(CUSTOMER_EMAIL)
                .fullName("Inactive Customer")
                .role(Role.ROLE_CUSTOMER)
                .status(AccountStatus.SUSPENDED)
                .build();

        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(inactiveUser));

        mockMvc.perform(post("/api/v1/assistant/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Send message succeeds for valid request returning 200 OK")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void sendMessage_validMessage_returns200() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));

        ToolExecutionRecord tool = ToolExecutionRecord.builder()
                .toolCallId("call_1")
                .toolName("track_parcel")
                .toolArguments("{\"trackingNumber\":\"SDP-1001\"}")
                .toolResult("{\"status\":\"IN_TRANSIT\"}")
                .success(true)
                .build();

        ChatTurnResponse turnResponse = ChatTurnResponse.builder()
                .sessionId(SESSION_ID)
                .answer("Parcel SDP-1001 is on the way.")
                .executedTools(List.of(tool))
                .createdAt(Instant.parse("2026-11-14T10:05:00Z"))
                .build();

        when(assistantChatService.sendMessage(eq(SESSION_ID), eq("Where is my parcel?"), eq(CUSTOMER_ID)))
                .thenReturn(turnResponse);

        SendMessageRequest request = SendMessageRequest.builder()
                .message("Where is my parcel?")
                .build();

        mockMvc.perform(post("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.answer").value("Parcel SDP-1001 is on the way."))
                .andExpect(jsonPath("$.executedTools[0].toolName").value("track_parcel"));
    }

    @Test
    @DisplayName("Send message with empty body is rejected with 400 Bad Request")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void sendMessage_emptyMessage_returns400() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));

        SendMessageRequest request = SendMessageRequest.builder()
                .message("   ")
                .build();

        mockMvc.perform(post("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Send message with excessively long text (> 1000 characters) is rejected with 400 Bad Request")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void sendMessage_excessivelyLongMessage_returns400() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));

        SendMessageRequest request = SendMessageRequest.builder()
                .message("x".repeat(1001))
                .build();

        mockMvc.perform(post("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Send message to nonexistent session returns 404 Not Found")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void sendMessage_nonexistentSession_returns404() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));
        when(assistantChatService.sendMessage(eq("session-unknown"), any(), eq(CUSTOMER_ID)))
                .thenThrow(new SessionNotFoundException("Chat session not found: session-unknown"));

        SendMessageRequest request = SendMessageRequest.builder()
                .message("Hello")
                .build();

        mockMvc.perform(post("/api/v1/assistant/sessions/{sessionId}/messages", "session-unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Chat Session Not Found"));
    }

    @Test
    @DisplayName("Send message to another customer's session returns 403 Forbidden")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void sendMessage_crossCustomerAccess_returns403() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));
        when(assistantChatService.sendMessage(eq(SESSION_ID), any(), eq(CUSTOMER_ID)))
                .thenThrow(new AccessDeniedException("You do not have permission to access chat session: " + SESSION_ID));

        SendMessageRequest request = SendMessageRequest.builder()
                .message("Hello")
                .build();

        mockMvc.perform(post("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI provider failure returns 502 Bad Gateway")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void sendMessage_aiProviderFailure_returns502() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));
        when(assistantChatService.sendMessage(eq(SESSION_ID), any(), eq(CUSTOMER_ID)))
                .thenThrow(new AiServiceException("Upstream Generative AI provider unavailable"));

        SendMessageRequest request = SendMessageRequest.builder()
                .message("Check parcel")
                .build();

        mockMvc.perform(post("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("AI Service Unavailable"));
    }

    @Test
    @DisplayName("Retrieve conversation history succeeds returning 200 OK and ordered messages")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void getSessionHistory_success_returns200() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));

        ChatMessageResponse msg1 = ChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .sessionId(SESSION_ID)
                .sender(MessageSender.USER)
                .content("Where is my parcel?")
                .createdAt(Instant.parse("2026-11-14T10:00:00Z"))
                .build();

        ChatMessageResponse msg2 = ChatMessageResponse.builder()
                .id(UUID.randomUUID())
                .sessionId(SESSION_ID)
                .sender(MessageSender.ASSISTANT)
                .content("Your parcel is in transit.")
                .toolName("track_parcel")
                .createdAt(Instant.parse("2026-11-14T10:00:02Z"))
                .build();

        when(assistantChatService.getSessionHistory(SESSION_ID, CUSTOMER_ID))
                .thenReturn(List.of(msg1, msg2));

        mockMvc.perform(get("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sender").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Where is my parcel?"))
                .andExpect(jsonPath("$[1].sender").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].content").value("Your parcel is in transit."))
                .andExpect(jsonPath("$[1].toolName").value("track_parcel"));
    }

    @Test
    @DisplayName("Retrieve history for nonexistent session returns 404 Not Found")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void getSessionHistory_nonexistentSession_returns404() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));
        when(assistantChatService.getSessionHistory(eq("session-unknown"), eq(CUSTOMER_ID)))
                .thenThrow(new SessionNotFoundException("Chat session not found: session-unknown"));

        mockMvc.perform(get("/api/v1/assistant/sessions/{sessionId}/messages", "session-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Chat Session Not Found"));
    }

    @Test
    @DisplayName("Customer A attempting to retrieve Customer B's session history returns 403 Forbidden")
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void getSessionHistory_crossCustomerAccess_returns403() throws Exception {
        when(userRepository.findByEmail(CUSTOMER_EMAIL)).thenReturn(Optional.of(activeCustomer));
        when(assistantChatService.getSessionHistory(eq(SESSION_ID), eq(CUSTOMER_ID)))
                .thenThrow(new AccessDeniedException("You do not have permission to access chat session: " + SESSION_ID));

        mockMvc.perform(get("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to retrieve session history returns 401 Unauthorized")
    void getSessionHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/sessions/{sessionId}/messages", SESSION_ID))
                .andExpect(status().isUnauthorized());
    }
}
