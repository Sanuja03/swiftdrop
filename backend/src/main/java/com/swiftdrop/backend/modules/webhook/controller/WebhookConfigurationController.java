package com.swiftdrop.backend.modules.webhook.controller;

import java.security.Principal;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.backend.modules.webhook.dto.WebhookConfigurationRequest;
import com.swiftdrop.backend.modules.webhook.dto.WebhookConfigurationResponse;
import com.swiftdrop.backend.modules.webhook.service.WebhookConfigurationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebhookConfigurationController.PATH)
@PreAuthorize("hasRole('MERCHANT')")
@RequiredArgsConstructor
public class WebhookConfigurationController {
    public static final String PATH = "/api/v1/merchant/webhooks/config";
    private final WebhookConfigurationService configurations;

    @GetMapping
    public ResponseEntity<WebhookConfigurationResponse> get(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(configurations.get(principal.getName()));
    }

    @PutMapping
    public ResponseEntity<WebhookConfigurationResponse> put(Principal principal,
            @Valid @RequestBody WebhookConfigurationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(configurations.put(principal.getName(), request.url()));
    }
}
