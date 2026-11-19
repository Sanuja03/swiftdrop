package com.swiftdrop.backend.modules.notifications.dto;

import java.util.UUID;
import com.swiftdrop.backend.modules.merchant.event.MerchantApprovedPayload;
import com.swiftdrop.backend.modules.merchant.event.MerchantRejectedPayload;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import tools.jackson.databind.json.JsonMapper;

/** Shared message rendering for the independent email and in-app channels. */
public record MerchantDecisionContent(UUID userId, String title, String message, String type) {
    public static MerchantDecisionContent from(DomainEventMessage event, JsonMapper mapper) {
        if (MerchantApprovedPayload.EVENT_TYPE.equals(event.eventType())) {
            var value = mapper.treeToValue(event.payload(), MerchantApprovedPayload.class);
            if (value.userId() == null) throw new IllegalArgumentException("Approval recipient is required");
            return new MerchantDecisionContent(value.userId(), "Merchant Application Approved",
                    "Your merchant application for " + value.businessName() + " has been approved.\n\n"
                    + "After verifying your email address, you can sign in to SwiftDrop and access merchant services.", "MERCHANT_APPROVED");
        }
        if (MerchantRejectedPayload.EVENT_TYPE.equals(event.eventType())) {
            var value = mapper.treeToValue(event.payload(), MerchantRejectedPayload.class);
            if (value.userId() == null || value.reason() == null || value.reason().isBlank()) {
                throw new IllegalArgumentException("Rejection recipient and reason are required");
            }
            return new MerchantDecisionContent(value.userId(), "Merchant Application Rejected",
                    "Your merchant application for " + value.businessName() + " was rejected.\n\nReason:\n"
                    + value.reason() + "\n\nYour merchant services remain unavailable. Please contact SwiftDrop support if you need clarification.", "MERCHANT_REJECTED");
        }
        return null;
    }
}
