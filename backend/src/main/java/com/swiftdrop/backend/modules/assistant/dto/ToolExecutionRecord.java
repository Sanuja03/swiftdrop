package com.swiftdrop.backend.modules.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionRecord {

    private String toolCallId;
    private String toolName;
    private String toolArguments;
    private String toolResult;
    private boolean success;
}
