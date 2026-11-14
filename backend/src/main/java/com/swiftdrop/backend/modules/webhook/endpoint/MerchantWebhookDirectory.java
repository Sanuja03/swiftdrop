package com.swiftdrop.backend.modules.webhook.endpoint;

import java.util.Optional;
import java.util.UUID;

/**
 * Everything the webhook engine needs to know about merchants, behind one interface so
 * the engine does not depend on how merchants and their endpoints are stored.
 *
 * <p>Merchants (merchants, parcels.merchant_id) and webhook endpoints (webhook_endpoints)
 * are owned by other modules. When those modules provide their own lookups, replace
 * {@link JdbcMerchantWebhookDirectory} with an implementation that calls them.
 */
public interface MerchantWebhookDirectory {

    /**
     * @return the merchant that booked this parcel, or empty for a customer booking
     */
    Optional<UUID> findMerchantIdForParcel(UUID parcelId);

    /**
     * Equivalent of getWebhookEndpoint() from the webhook setup module.
     *
     * @return empty when the merchant has not configured a webhook URL
     */
    Optional<WebhookEndpoint> findEndpoint(UUID merchantId);

    /**
     * @param email the logged-in user's email (the JWT principal name)
     * @return the merchant account owned by this user, if any
     */
    Optional<UUID> findMerchantIdForUser(String email);
}
