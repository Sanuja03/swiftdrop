package com.swiftdrop.backend.modules.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.assistant.client.AiChatClient;
import com.swiftdrop.backend.modules.assistant.client.dto.*;
import com.swiftdrop.backend.modules.assistant.config.AiProperties;
import com.swiftdrop.backend.modules.assistant.dto.*;
import com.swiftdrop.backend.modules.assistant.entity.ChatMessage;
import com.swiftdrop.backend.modules.assistant.entity.MessageSender;
import com.swiftdrop.backend.modules.assistant.exception.AiServiceException;
import com.swiftdrop.backend.modules.assistant.service.impl.AiOrchestrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiOrchestrationServiceImplTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private AssistantToolService assistantToolService;

    private AiProperties aiProperties;
    private ObjectMapper objectMapper;
    private AiOrchestrationServiceImpl orchestrationService;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setModel("gpt-4o-mini");
        aiProperties.setMaxIterations(5);

        objectMapper = new ObjectMapper();

        orchestrationService = new AiOrchestrationServiceImpl(
                aiChatClient,
                assistantToolService,
                aiProperties,
                objectMapper
        );
    }

    // =========================================================================
    // 1. Normal AI response without tool calls
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage returns normal conversational answer when no tools are called")
    void processCustomerMessage_normalResponseWithoutToolCalls() {
        ChatCompletionResponse response = createTextResponse("Hello! How can I help with your SwiftDrop delivery today?");
        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class))).thenReturn(response);

        AiChatResponse result = orchestrationService.processCustomerMessage("Hi there", null, Collections.emptyList());

        assertThat(result.getAnswer()).isEqualTo("Hello! How can I help with your SwiftDrop delivery today?");
        assertThat(result.getExecutedTools()).isEmpty();
        verifyNoInteractions(assistantToolService);
    }

    // =========================================================================
    // 2. AI request that calls track_parcel
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage executes track_parcel tool and grounds final answer in results")
    void processCustomerMessage_executesTrackParcel() {
        // Turn 1: Model requests tool track_parcel
        ToolCallDto toolCall = createToolCall("call_track_1", "track_parcel", "{\"trackingNumber\":\"SDP1234567\"}");
        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);

        // Turn 2: Model returns natural language answer grounded in tracking result
        ChatCompletionResponse turn2 = createTextResponse("Your parcel SDP1234567 is currently In Transit from Colombo to Kandy.");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        TrackParcelResult trackingResult = TrackParcelResult.builder()
                .found(true)
                .trackingNumber("SDP1234567")
                .status("IN_TRANSIT")
                .statusLabel("In Transit")
                .originCity("Colombo")
                .destinationCity("Kandy")
                .build();

        when(assistantToolService.trackParcel("SDP1234567")).thenReturn(trackingResult);

        AiChatResponse result = orchestrationService.processCustomerMessage("Where is my parcel SDP1234567?", null, Collections.emptyList());

        assertThat(result.getAnswer()).contains("In Transit from Colombo to Kandy");
        assertThat(result.getExecutedTools()).hasSize(1);

        ToolExecutionRecord record = result.getExecutedTools().get(0);
        assertThat(record.getToolName()).isEqualTo("track_parcel");
        assertThat(record.getToolArguments()).contains("SDP1234567");
        assertThat(record.isSuccess()).isTrue();

        verify(assistantToolService).trackParcel("SDP1234567");
    }

    // =========================================================================
    // 3. AI request that calls estimate_fee
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage executes estimate_fee tool with arguments")
    void processCustomerMessage_executesEstimateFee() {
        ToolCallDto toolCall = createToolCall("call_est_1", "estimate_fee",
                "{\"pickupCity\":\"Colombo\",\"dropoffCity\":\"Kandy\",\"weightKg\":2.0,\"serviceType\":\"STANDARD\"}");
        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);
        ChatCompletionResponse turn2 = createTextResponse("The delivery fee from Colombo to Kandy for 2.0 kg is LKR 1040.00.");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        EstimateFeeToolResult feeResult = EstimateFeeToolResult.builder()
                .success(true)
                .totalFee(new BigDecimal("1040.00"))
                .currency("LKR")
                .pickupZone("Colombo")
                .dropoffZone("Kandy")
                .build();

        when(assistantToolService.estimateFee(any(EstimateFeeToolRequest.class))).thenReturn(feeResult);

        AiChatResponse result = orchestrationService.processCustomerMessage(
                "How much to send 2 kg from Colombo to Kandy?",
                null,
                Collections.emptyList()
        );

        assertThat(result.getAnswer()).contains("LKR 1040.00");
        assertThat(result.getExecutedTools()).hasSize(1);

        ArgumentCaptor<EstimateFeeToolRequest> captor = ArgumentCaptor.forClass(EstimateFeeToolRequest.class);
        verify(assistantToolService).estimateFee(captor.capture());
        EstimateFeeToolRequest actual = captor.getValue();
        assertThat(actual.getPickupCity()).isEqualTo("Colombo");
        assertThat(actual.getDropoffCity()).isEqualTo("Kandy");
        assertThat(actual.getWeightKg()).isEqualTo(new BigDecimal("2.0"));
    }

    // =========================================================================
    // 4. AI request that calls list_my_parcels
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage executes list_my_parcels for authenticated customer")
    void processCustomerMessage_executesListMyParcels() {
        ToolCallDto toolCall = createToolCall("call_list_1", "list_my_parcels", "{}");
        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);
        ChatCompletionResponse turn2 = createTextResponse("You have 1 active parcel (SDP1000001) in transit to Kandy.");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        CustomerParcelSummary summary = CustomerParcelSummary.builder()
                .trackingNumber("SDP1000001")
                .status("IN_TRANSIT")
                .dropoffCity("Kandy")
                .fee(new BigDecimal("840.00"))
                .build();

        when(assistantToolService.listMyParcels(CUSTOMER_ID)).thenReturn(List.of(summary));

        AiChatResponse result = orchestrationService.processCustomerMessage(
                "Show my packages",
                CUSTOMER_ID,
                Collections.emptyList()
        );

        assertThat(result.getAnswer()).contains("SDP1000001");
        assertThat(result.getExecutedTools()).hasSize(1);
        verify(assistantToolService).listMyParcels(CUSTOMER_ID);
    }

    // =========================================================================
    // 5. Authentication safety: list_my_parcels with unauthenticated user
    // =========================================================================

    @Test
    @DisplayName("list_my_parcels rejects unauthenticated caller and does not invoke repository")
    void processCustomerMessage_unauthenticatedListMyParcels() {
        ToolCallDto toolCall = createToolCall("call_list_guest", "list_my_parcels", "{}");
        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);
        ChatCompletionResponse turn2 = createTextResponse("Please sign in to view your personal shipments.");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        AiChatResponse result = orchestrationService.processCustomerMessage(
                "Show my parcels",
                null, // guest
                Collections.emptyList()
        );

        assertThat(result.getAnswer()).contains("sign in");
        assertThat(result.getExecutedTools()).hasSize(1);
        ToolExecutionRecord record = result.getExecutedTools().get(0);
        assertThat(record.isSuccess()).isFalse();
        assertThat(record.getToolResult()).contains("Customer is not signed in");

        verify(assistantToolService, never()).listMyParcels(any());
    }

    // =========================================================================
    // 6. Model attempts to supply customer ID in arguments (Zero Trust)
    // =========================================================================

    @Test
    @DisplayName("Zero Trust: customer ID in model arguments is ignored; only authenticated identity is used")
    void processCustomerMessage_ignoresModelSuppliedCustomerId() {
        UUID attackerSuppliedId = UUID.randomUUID();
        ToolCallDto toolCall = createToolCall("call_spoof", "list_my_parcels",
                "{\"customerId\":\"" + attackerSuppliedId + "\"}");

        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);
        ChatCompletionResponse turn2 = createTextResponse("Here are your parcels.");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        when(assistantToolService.listMyParcels(CUSTOMER_ID)).thenReturn(Collections.emptyList());

        orchestrationService.processCustomerMessage("Show my parcels", CUSTOMER_ID, Collections.emptyList());

        // Verifies the service was queried with the verified CUSTOMER_ID, NOT the attackerSuppliedId
        verify(assistantToolService).listMyParcels(CUSTOMER_ID);
        verify(assistantToolService, never()).listMyParcels(attackerSuppliedId);
    }

    // =========================================================================
    // 7. Unknown tool names
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage safely reports error when model calls unknown tool")
    void processCustomerMessage_unknownToolName() {
        ToolCallDto toolCall = createToolCall("call_unk", "delete_all_parcels", "{}");
        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);
        ChatCompletionResponse turn2 = createTextResponse("I cannot perform that action.");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        AiChatResponse result = orchestrationService.processCustomerMessage("Do something unauthorized", null, Collections.emptyList());

        assertThat(result.getExecutedTools()).hasSize(1);
        ToolExecutionRecord record = result.getExecutedTools().get(0);
        assertThat(record.isSuccess()).isFalse();
        assertThat(record.getToolResult()).contains("Unknown tool name: delete_all_parcels");
    }

    // =========================================================================
    // 8. Invalid tool arguments (malformed JSON)
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage handles malformed tool JSON arguments safely")
    void processCustomerMessage_invalidToolArguments() {
        ToolCallDto toolCall = createToolCall("call_malformed", "estimate_fee", "{not valid json}");
        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);
        ChatCompletionResponse turn2 = createTextResponse("Could you please specify the pickup and destination cities?");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        when(assistantToolService.estimateFee(any())).thenReturn(
                EstimateFeeToolResult.builder().success(false).message("Missing required pricing input").build()
        );

        AiChatResponse result = orchestrationService.processCustomerMessage("Estimate fee", null, Collections.emptyList());

        assertThat(result.getExecutedTools()).hasSize(1);
        verify(assistantToolService).estimateFee(any());
    }

    // =========================================================================
    // 9. Tool execution failure (tool throws exception)
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage handles tool exception without crashing conversational turn")
    void processCustomerMessage_toolThrowsException() {
        ToolCallDto toolCall = createToolCall("call_fail", "track_parcel", "{\"trackingNumber\":\"CRASH123\"}");
        ChatCompletionResponse turn1 = createToolCallResponse(toolCall);
        ChatCompletionResponse turn2 = createTextResponse("Sorry, tracking service is currently encountering an issue.");

        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenReturn(turn1)
                .thenReturn(turn2);

        when(assistantToolService.trackParcel("CRASH123")).thenThrow(new RuntimeException("Database timeout"));

        AiChatResponse result = orchestrationService.processCustomerMessage("Track CRASH123", null, Collections.emptyList());

        assertThat(result.getExecutedTools()).hasSize(1);
        ToolExecutionRecord record = result.getExecutedTools().get(0);
        assertThat(record.isSuccess()).isFalse();
        assertThat(record.getToolResult()).contains("Tool execution encountered an internal error");
        assertThat(record.getToolResult()).doesNotContain("Database timeout");
    }

    // =========================================================================
    // 10. Provider API failure
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage propagates AiServiceException when AI provider fails")
    void processCustomerMessage_providerApiFailure() {
        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class)))
                .thenThrow(new AiServiceException("AI provider returned error code: 503"));

        assertThatThrownBy(() -> orchestrationService.processCustomerMessage("Hello", null, Collections.emptyList()))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("503");
    }

    // =========================================================================
    // 11. Maximum tool-call iteration protection (infinite loop prevention)
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage terminates cleanly when max tool-calling iterations is reached")
    void processCustomerMessage_maxIterationsProtection() {
        aiProperties.setMaxIterations(3);

        ToolCallDto toolCall = createToolCall("call_loop", "track_parcel", "{\"trackingNumber\":\"SDP123\"}");
        ChatCompletionResponse infiniteToolResponse = createToolCallResponse(toolCall);

        // Always returns another tool call, simulating infinite loop
        when(aiChatClient.sendChatCompletion(any(ChatCompletionRequest.class))).thenReturn(infiniteToolResponse);

        when(assistantToolService.trackParcel(any())).thenReturn(
                TrackParcelResult.builder().found(true).trackingNumber("SDP123").status("CREATED").build()
        );

        AiChatResponse result = orchestrationService.processCustomerMessage("Loop me", null, Collections.emptyList());

        // Must stop at maxIterations = 3
        verify(aiChatClient, times(3)).sendChatCompletion(any(ChatCompletionRequest.class));
        assertThat(result.getExecutedTools()).hasSize(3);
        assertThat(result.getAnswer()).isNotBlank();
    }

    // =========================================================================
    // 12. Input validation: null or blank message
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage rejects null or blank user message")
    void processCustomerMessage_rejectsBlankMessage() {
        assertThatThrownBy(() -> orchestrationService.processCustomerMessage(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> orchestrationService.processCustomerMessage("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // 13. Conversation history integration
    // =========================================================================

    @Test
    @DisplayName("processCustomerMessage includes previous conversation history in request")
    void processCustomerMessage_includesConversationHistory() {
        ChatMessage h1 = ChatMessage.builder()
                .sessionId("sess-1")
                .sender(MessageSender.USER)
                .content("Hi")
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        ChatMessage h2 = ChatMessage.builder()
                .sessionId("sess-1")
                .sender(MessageSender.ASSISTANT)
                .content("Hello, how can I help?")
                .createdAt(Instant.now().minusSeconds(50))
                .build();

        when(aiChatClient.sendChatCompletion(any())).thenReturn(createTextResponse("Sure!"));

        orchestrationService.processCustomerMessage("Track my parcel", null, List.of(h1, h2));

        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        verify(aiChatClient).sendChatCompletion(captor.capture());

        List<ChatMessageDto> messages = captor.getValue().getMessages();
        // system (0), h1 (1), h2 (2), current user (3)
        assertThat(messages).hasSize(4);
        assertThat(messages.get(1).getRole()).isEqualTo("user");
        assertThat(messages.get(1).getContent()).isEqualTo("Hi");
        assertThat(messages.get(2).getRole()).isEqualTo("assistant");
        assertThat(messages.get(2).getContent()).isEqualTo("Hello, how can I help?");
        assertThat(messages.get(3).getRole()).isEqualTo("user");
        assertThat(messages.get(3).getContent()).isEqualTo("Track my parcel");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private ChatCompletionResponse createTextResponse(String content) {
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content(content)
                .build();

        ChoiceDto choice = ChoiceDto.builder()
                .index(0)
                .message(message)
                .finishReason("stop")
                .build();

        return ChatCompletionResponse.builder()
                .id("chatcmpl-test")
                .choices(List.of(choice))
                .build();
    }

    private ChatCompletionResponse createToolCallResponse(ToolCallDto toolCall) {
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .toolCalls(List.of(toolCall))
                .build();

        ChoiceDto choice = ChoiceDto.builder()
                .index(0)
                .message(message)
                .finishReason("tool_calls")
                .build();

        return ChatCompletionResponse.builder()
                .id("chatcmpl-test-tool")
                .choices(List.of(choice))
                .build();
    }

    private ToolCallDto createToolCall(String id, String name, String argumentsJson) {
        return ToolCallDto.builder()
                .id(id)
                .type("function")
                .function(FunctionCallDto.builder()
                        .name(name)
                        .arguments(argumentsJson)
                        .build())
                .build();
    }
}
