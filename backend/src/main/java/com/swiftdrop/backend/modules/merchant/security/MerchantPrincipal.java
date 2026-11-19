package com.swiftdrop.backend.modules.merchant.security;

import java.util.UUID;

import com.swiftdrop.backend.modules.merchant.entity.Merchant;

/** Principal of a request authenticated with a merchant API key. Holds ids only, never the key. */
public record MerchantPrincipal(UUID merchantId, UUID userId) {

    public static MerchantPrincipal of(Merchant merchant) {
        return new MerchantPrincipal(merchant.getId(), merchant.getUserId());
    }

    @Override
    public String toString() {
        return "merchant:" + merchantId;
    }
}
