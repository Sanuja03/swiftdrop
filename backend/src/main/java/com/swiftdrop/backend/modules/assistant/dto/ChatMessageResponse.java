package com.swiftdrop.backend.modules.assistant.dto;

import com.swiftdrop.backend.modules.assistant.entity.MessageSender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    private UUID id;
    private String sessionId;
    private MessageSender sender;
    private String content;
    private String toolName;
    private Instant createdAt;
}
