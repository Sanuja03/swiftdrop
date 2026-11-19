package com.swiftdrop.backend.modules.merchant.event;

import java.util.UUID;

public record MerchantApprovedPayload(UUID merchantId, UUID userId, String businessName) {
    public static final String EVENT_TYPE = "MERCHANT_APPROVED";
    public static final String ROUTING_KEY = "merchant.approved";
}
