package com.swiftdrop.backend.modules.assistant.controller;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.exception.AccountNotActiveException;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.assistant.dto.ChatMessageResponse;
import com.swiftdrop.backend.modules.assistant.dto.ChatTurnResponse;
import com.swiftdrop.backend.modules.assistant.dto.CreateSessionResponse;
import com.swiftdrop.backend.modules.assistant.dto.SendMessageRequest;
import com.swiftdrop.backend.modules.assistant.service.AssistantChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class AssistantChatController {

    private final AssistantChatService assistantChatService;
    private final UserRepository userRepository;

    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession(Principal principal) {
        UUID customerId = resolveCustomerId(principal);
        CreateSessionResponse response = assistantChatService.createSession(customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatTurnResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SendMessageRequest request,
            Principal principal) {
        UUID customerId = resolveCustomerId(principal);
        ChatTurnResponse response = assistantChatService.sendMessage(
                sessionId,
                request.getMessage(),
                customerId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getSessionHistory(
            @PathVariable String sessionId,
            Principal principal) {
        UUID customerId = resolveCustomerId(principal);
        List<ChatMessageResponse> history = assistantChatService.getSessionHistory(sessionId, customerId);
        return ResponseEntity.ok(history);
    }

    private UUID resolveCustomerId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("No authenticated customer principal found");
        }

        User user = userRepository.findByEmail(principal.getName().trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Customer account not found for email: " + principal.getName()));

        if (user.getRole() != Role.ROLE_CUSTOMER) {
            throw new AccessDeniedException("User is not authorized as a customer: " + user.getId());
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Customer account is not active: " + user.getId());
        }

        return user.getId();
    }
}
