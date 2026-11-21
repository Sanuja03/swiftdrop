package com.swiftdrop.backend.modules.merchant.event;

import java.util.UUID;

/** Immutable decision snapshot, persisted atomically with the rejection. */
public record MerchantRejectedPayload(UUID merchantId, UUID userId, String businessName, String reason) {
    public static final String EVENT_TYPE = "MERCHANT_REJECTED";
    public static final String ROUTING_KEY = "merchant.rejected";
}
