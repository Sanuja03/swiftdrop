package com.swiftdrop.backend.modules.courier.service;

import org.springframework.stereotype.Component;

@Component
public class ProofInputValidator {

    public String validateRecipientName(String name) {
        return validateRequiredText(name, 150, "Recipient name");
    }

    public String validateChangeReason(String reason) {
        return validateRequiredText(reason, 500, "Change reason");
    }

    private String validateRequiredText(
            String value,
            int maxLength,
            String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        String cleanedValue = value.strip();

        if (cleanedValue.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed "
                            + maxLength + " characters"
            );
        }

        return cleanedValue;
    }
}