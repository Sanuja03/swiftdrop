package com.swiftdrop.backend.modules.assistant.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessageDto {

    private String role;
    private String content;
    private String name;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("tool_calls")
    private List<ToolCallDto> toolCalls;

    public static ChatMessageDto system(String content) {
        return ChatMessageDto.builder()
                .role("system")
                .content(content)
                .build();
    }

    public static ChatMessageDto user(String content) {
        return ChatMessageDto.builder()
                .role("user")
                .content(content)
                .build();
    }

    public static ChatMessageDto assistant(String content) {
        return ChatMessageDto.builder()
                .role("assistant")
                .content(content)
                .build();
    }

    public static ChatMessageDto tool(String toolCallId, String name, String content) {
        return ChatMessageDto.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .name(name)
                .content(content)
                .build();
    }
}
