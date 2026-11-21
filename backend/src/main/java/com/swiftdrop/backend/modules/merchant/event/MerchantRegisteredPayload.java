package com.swiftdrop.backend.modules.merchant.event;

import java.util.UUID;

/** Registration and this event are saved in the same transaction; no credentials leave the module. */
public record MerchantRegisteredPayload(UUID merchantId) {
    public static final String EVENT_TYPE = "MERCHANT_REGISTERED";
    public static final String ROUTING_KEY = "merchant.registered";
}
