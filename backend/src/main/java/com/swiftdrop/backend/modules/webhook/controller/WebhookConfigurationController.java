package com.swiftdrop.backend.modules.webhook.controller;

import java.security.Principal;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.webhook.dto.WebhookConfigurationRequest;
import com.swiftdrop.backend.modules.webhook.dto.WebhookConfigurationResponse;
import com.swiftdrop.backend.modules.webhook.service.WebhookConfigurationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class WebhookConfigurationController {
    public static final String PATH = "/api/v1/merchant/webhooks/config";
    public static final String LEGACY_PATH = "/api/v1/merchant/webhook";
    private final WebhookConfigurationService configurations;

    @GetMapping({PATH, LEGACY_PATH})
    public ResponseEntity<WebhookConfigurationResponse> get(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(configurations.get(principal.getName()));
    }

    @PutMapping({PATH, LEGACY_PATH})
    public ResponseEntity<WebhookConfigurationResponse> put(Principal principal,
            @Valid @RequestBody WebhookConfigurationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(configurations.put(principal.getName(), request.url()));
    }

    /** Explicit recovery for an existing configured secret; never part of a routine save. */
    @PostMapping({PATH + "/rotate-secret", LEGACY_PATH + "/rotate-secret"})
    public ResponseEntity<WebhookConfigurationResponse> rotate(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(configurations.rotate(principal.getName()));
    }
}
