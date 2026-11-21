package com.swiftdrop.backend.modules.assistant.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.backend.modules.assistant.client.AiChatClient;
import com.swiftdrop.backend.modules.assistant.client.dto.*;
import com.swiftdrop.backend.modules.assistant.config.AiProperties;
import com.swiftdrop.backend.modules.assistant.dto.*;
import com.swiftdrop.backend.modules.assistant.entity.ChatMessage;
import com.swiftdrop.backend.modules.assistant.entity.MessageSender;
import com.swiftdrop.backend.modules.assistant.service.AiOrchestrationService;
import com.swiftdrop.backend.modules.assistant.service.AssistantToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestrationServiceImpl implements AiOrchestrationService {

    private static final String SYSTEM_PROMPT = """
You are the SwiftDrop AI Delivery Assistant, a courteous and helpful virtual assistant for SwiftDrop, a premier courier service in Sri Lanka.

You have access to live backend tools:
1. `track_parcel`: Retrieve current status, location events, and estimated delivery for a tracking number.
2. `estimate_fee`: Calculate parcel delivery fee across Sri Lankan cities (e.g. Colombo, Kandy, Galle, Gampaha, Jaffna, etc.) based on pickup city, destination city, weight, and service type.
3. `list_my_parcels`: List parcels belonging to the currently logged-in customer.

STRICT INSTRUCTIONS:
- NEVER fabricate or guess tracking numbers, package delivery statuses, locations, or pricing figures. All delivery and pricing facts MUST come from actual tool execution results.
- If the user asks "Where is my parcel?" or asks to track a package without providing a tracking number, ask them to supply their tracking number (e.g. SDP1234567), or offer to list their account shipments if they are logged in.
- If the user asks for a price estimate but omits required details (e.g. "How much to send 2 kg to Kandy?" with no pickup city), politely ask for the missing details before calling `estimate_fee`.
- If a tool reports that a parcel is not found or a city is unknown, communicate that truthfully and suggest valid alternatives.
- Keep responses friendly, professional, clear, and concise.
""";

    private final AiChatClient aiChatClient;
    private final AssistantToolService assistantToolService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public AiChatResponse processCustomerMessage(
            String userMessage,
            UUID authenticatedCustomerId,
            List<ChatMessage> conversationHistory) {

        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("User message must not be null or blank");
        }

        List<ChatMessageDto> messages = new ArrayList<>();
        messages.add(ChatMessageDto.system(SYSTEM_PROMPT));

        // Add recent conversation history (up to last 10 turns)
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            int start = Math.max(0, conversationHistory.size() - 10);
            for (int i = start; i < conversationHistory.size(); i++) {
                ChatMessage historyMsg = conversationHistory.get(i);
                if (historyMsg.getSender() == MessageSender.USER) {
                    messages.add(ChatMessageDto.user(historyMsg.getContent()));
                } else if (historyMsg.getSender() == MessageSender.ASSISTANT) {
                    messages.add(ChatMessageDto.assistant(historyMsg.getContent()));
                }
            }
        }

        // Add current user turn
        messages.add(ChatMessageDto.user(userMessage.trim()));

        List<ToolDefinitionDto> toolDefinitions = buildToolDefinitions();
        List<ToolExecutionRecord> executedTools = new ArrayList<>();

        int maxIterations = Math.max(1, aiProperties.getMaxIterations());
        String finalAnswer = null;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(aiProperties.getModel())
                    .messages(new ArrayList<>(messages))
                    .tools(toolDefinitions)
                    .temperature(0.2)
                    .build();

            ChatCompletionResponse response = aiChatClient.sendChatCompletion(request);
            ChatMessageDto assistantMsg = response != null ? response.getFirstMessage() : null;

            if (assistantMsg == null) {
                log.warn("Received empty response from AI provider on iteration [{}]", iteration);
                break;
            }

            // Case A: Model responded with plain text (no tool calls)
            if (assistantMsg.getToolCalls() == null || assistantMsg.getToolCalls().isEmpty()) {
                finalAnswer = assistantMsg.getContent();
                break;
            }

            // Case B: Model requested one or more tool calls
            messages.add(assistantMsg);

            for (ToolCallDto toolCall : assistantMsg.getToolCalls()) {
                String toolCallId = toolCall.getId();
                String toolName = (toolCall.getFunction() != null) ? toolCall.getFunction().getName() : "";
                String argumentsJson = (toolCall.getFunction() != null) ? toolCall.getFunction().getArguments() : "{}";

                ToolExecutionResult execution = executeTool(toolName, argumentsJson, authenticatedCustomerId);

                executedTools.add(ToolExecutionRecord.builder()
                        .toolCallId(toolCallId)
                        .toolName(toolName)
                        .toolArguments(argumentsJson)
                        .toolResult(execution.outputJson())
                        .success(execution.success())
                        .build());

                messages.add(ChatMessageDto.tool(toolCallId, toolName, execution.outputJson()));
            }
        }

        if (finalAnswer == null || finalAnswer.isBlank()) {
            finalAnswer = "I have processed your delivery request. Please let me know if you need any additional information.";
        }

        return AiChatResponse.builder()
                .answer(finalAnswer)
                .executedTools(executedTools)
                .build();
    }

    private ToolExecutionResult executeTool(String toolName, String argumentsJson, UUID authenticatedCustomerId) {
        try {
            return switch (toolName) {
                case "track_parcel" -> executeTrackParcel(argumentsJson);
                case "estimate_fee" -> executeEstimateFee(argumentsJson);
                case "list_my_parcels" -> executeListMyParcels(authenticatedCustomerId);
                default -> {
                    log.warn("AI model requested unknown tool [{}]", toolName);
                    yield new ToolExecutionResult(false, "{\"success\": false, \"error\": \"Unknown tool name: " + toolName + "\"}");
                }
            };
        } catch (Exception ex) {
            log.error("Unexpected error executing tool [{}]", toolName, ex);
            return new ToolExecutionResult(false,
                    "{\"success\": false, \"error\": \"Tool execution encountered an internal error. Please inform the user that this service is temporarily unavailable.\"}");
        }
    }

    private ToolExecutionResult executeTrackParcel(String argumentsJson) {
        String trackingNumber = null;
        try {
            JsonNode root = objectMapper.readTree(argumentsJson);
            if (root.has("trackingNumber") && !root.get("trackingNumber").isNull()) {
                trackingNumber = root.get("trackingNumber").asText();
            }
        } catch (Exception ex) {
            log.warn("Failed to parse track_parcel arguments: [{}]", argumentsJson);
        }

        TrackParcelResult result = assistantToolService.trackParcel(trackingNumber);
        try {
            return new ToolExecutionResult(result.isFound(), objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tracking result", e);
            return new ToolExecutionResult(false, "{\"success\": false, \"error\": \"Failed to format tracking result.\"}");
        }
    }

    private ToolExecutionResult executeEstimateFee(String argumentsJson) {
        EstimateFeeToolRequest toolRequest;
        try {
            JsonNode root = objectMapper.readTree(argumentsJson);
            String pickup = root.hasNonNull("pickupCity") ? root.get("pickupCity").asText() : null;
            String dropoff = root.hasNonNull("dropoffCity") ? root.get("dropoffCity").asText() : null;
            BigDecimal weight = root.hasNonNull("weightKg") ? new BigDecimal(root.get("weightKg").asText()) : null;
            String serviceType = root.hasNonNull("serviceType") ? root.get("serviceType").asText() : null;
            String parcelSize = root.hasNonNull("parcelSize") ? root.get("parcelSize").asText() : null;

            toolRequest = EstimateFeeToolRequest.builder()
                    .pickupCity(pickup)
                    .dropoffCity(dropoff)
                    .weightKg(weight)
                    .serviceType(serviceType)
                    .parcelSize(parcelSize)
                    .build();
        } catch (Exception ex) {
            log.warn("Failed to parse estimate_fee arguments: [{}]", argumentsJson);
            toolRequest = EstimateFeeToolRequest.builder().build();
        }

        EstimateFeeToolResult result = assistantToolService.estimateFee(toolRequest);
        try {
            return new ToolExecutionResult(result.isSuccess(), objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize fee estimation result", e);
            return new ToolExecutionResult(false, "{\"success\": false, \"error\": \"Failed to format fee estimation result.\"}");
        }
    }

    private ToolExecutionResult executeListMyParcels(UUID authenticatedCustomerId) {
        // Zero Trust: authenticatedCustomerId comes strictly from backend security context, NEVER from argumentsJson.
        if (authenticatedCustomerId == null) {
            return new ToolExecutionResult(false,
                    "{\"authenticated\": false, \"message\": \"Customer is not signed in. Please log in to your SwiftDrop account to view your shipments.\"}");
        }

        List<CustomerParcelSummary> summaries = assistantToolService.listMyParcels(authenticatedCustomerId);
        try {
            return new ToolExecutionResult(true, objectMapper.writeValueAsString(summaries));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize parcel list", e);
            return new ToolExecutionResult(false, "{\"success\": false, \"error\": \"Failed to format parcel list.\"}");
        }
    }

    private List<ToolDefinitionDto> buildToolDefinitions() {
        // 1. track_parcel definition
        Map<String, Object> trackParcelProps = new LinkedHashMap<>();
        trackParcelProps.put("trackingNumber", Map.of(
                "type", "string",
                "description", "The unique parcel tracking number, e.g. SDP1234567"
        ));
        Map<String, Object> trackParcelParams = Map.of(
                "type", "object",
                "properties", trackParcelProps,
                "required", List.of("trackingNumber")
        );
        ToolDefinitionDto trackParcelTool = ToolDefinitionDto.ofFunction(FunctionDefinitionDto.builder()
                .name("track_parcel")
                .description("Retrieve tracking details, status milestones, and estimated delivery for a SwiftDrop parcel using its tracking number.")
                .parameters(trackParcelParams)
                .build());

        // 2. estimate_fee definition
        Map<String, Object> estimateFeeProps = new LinkedHashMap<>();
        estimateFeeProps.put("pickupCity", Map.of(
                "type", "string",
                "description", "Origin city or district in Sri Lanka (e.g. Colombo, Kandy, Galle, Gampaha)"
        ));
        estimateFeeProps.put("dropoffCity", Map.of(
                "type", "string",
                "description", "Destination city or district in Sri Lanka (e.g. Kandy, Jaffna, Galle)"
        ));
        estimateFeeProps.put("weightKg", Map.of(
                "type", "number",
                "description", "Parcel weight in kilograms (must be positive, max 30 kg)"
        ));
        estimateFeeProps.put("serviceType", Map.of(
                "type", "string",
                "enum", List.of("STANDARD", "EXPRESS", "SAME_DAY"),
                "description", "Delivery service speed tier. Defaults to STANDARD if not specified."
        ));
        estimateFeeProps.put("parcelSize", Map.of(
                "type", "string",
                "enum", List.of("DOCUMENTS", "SMALL", "MEDIUM", "LARGE"),
                "description", "Optional size category (DOCUMENTS, SMALL, MEDIUM, LARGE)."
        ));
        Map<String, Object> estimateFeeParams = Map.of(
                "type", "object",
                "properties", estimateFeeProps,
                "required", List.of("pickupCity", "dropoffCity", "weightKg")
        );
        ToolDefinitionDto estimateFeeTool = ToolDefinitionDto.ofFunction(FunctionDefinitionDto.builder()
                .name("estimate_fee")
                .description("Calculate parcel delivery fee between Sri Lankan cities based on weight and service type.")
                .parameters(estimateFeeParams)
                .build());

        // 3. list_my_parcels definition
        Map<String, Object> listParcelsParams = Map.of(
                "type", "object",
                "properties", Collections.emptyMap()
        );
        ToolDefinitionDto listMyParcelsTool = ToolDefinitionDto.ofFunction(FunctionDefinitionDto.builder()
                .name("list_my_parcels")
                .description("List parcels and shipments belonging to the currently logged-in customer. Requires user to be logged in.")
                .parameters(listParcelsParams)
                .build());

        return List.of(trackParcelTool, estimateFeeTool, listMyParcelsTool);
    }

    private record ToolExecutionResult(boolean success, String outputJson) {}
}
