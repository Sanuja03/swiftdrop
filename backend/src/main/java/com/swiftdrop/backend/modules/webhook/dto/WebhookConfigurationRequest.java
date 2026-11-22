package com.swiftdrop.backend.modules.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Identity and signing secrets are deliberately not caller-controlled. */
public record WebhookConfigurationRequest(
        @com.fasterxml.jackson.annotation.JsonAlias("webhookUrl") @NotBlank @Size(max = 500) String url) {
}
