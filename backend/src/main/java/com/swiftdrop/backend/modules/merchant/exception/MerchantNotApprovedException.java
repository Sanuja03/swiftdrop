package com.swiftdrop.backend.modules.merchant.exception;

public class MerchantNotApprovedException extends RuntimeException {
    public MerchantNotApprovedException(String message) {
        super(message);
    }
}

