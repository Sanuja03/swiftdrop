package com.swiftdrop.backend.modules.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurnResponse {

    private String sessionId;
    private String answer;
    private List<ToolExecutionRecord> executedTools;
    private Instant createdAt;
}
