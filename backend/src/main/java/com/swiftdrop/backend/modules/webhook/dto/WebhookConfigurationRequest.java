package com.swiftdrop.backend.modules.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Identity and signing secrets are deliberately not caller-controlled. */
public record WebhookConfigurationRequest(@NotBlank @Size(max = 500) String url) {
}
