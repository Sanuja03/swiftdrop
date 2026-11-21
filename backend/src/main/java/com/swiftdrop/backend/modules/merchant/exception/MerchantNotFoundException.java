package com.swiftdrop.backend.modules.merchant.exception;

import java.util.UUID;

public class MerchantNotFoundException extends RuntimeException {
    public MerchantNotFoundException(String message) {
        super(message);
    }

    public MerchantNotFoundException(UUID merchantId) {
        super("Merchant not found with ID: " + merchantId);
    }
}

