package com.swiftdrop.backend.modules.webhook.endpoint;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads merchant data with plain SQL, because the merchant entities live on another
 * branch (feature/merchant-api). Uses the columns that branch adds: parcels.merchant_id,
 * merchants.user_id, merchants.webhook_url and merchants.webhook_secret.
 *
 * <p>If those tables or columns do not exist yet, every lookup returns empty, so parcels
 * simply produce no webhooks instead of failing.
 *
 * <p>TODO(webhook-setup): read the URL and secret from webhook_endpoints via
 * getWebhookEndpoint() once that module is merged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcMerchantWebhookDirectory implements MerchantWebhookDirectory {

    static final String MERCHANT_FOR_PARCEL_SQL = "SELECT merchant_id FROM parcels WHERE id = ?";

    static final String ENDPOINT_SQL = """
            SELECT webhook_url, webhook_secret FROM merchants
            WHERE id = ? AND webhook_url IS NOT NULL AND webhook_url <> ''
              AND webhook_secret IS NOT NULL AND webhook_secret <> ''
            """;

    static final String MERCHANT_FOR_USER_SQL = """
            SELECT m.id FROM merchants m JOIN users u ON u.id = m.user_id
            WHERE u.email = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<UUID> findMerchantIdForParcel(UUID parcelId) {
        return queryForUuid(MERCHANT_FOR_PARCEL_SQL, parcelId);
    }

    @Override
    public Optional<WebhookEndpoint> findEndpoint(UUID merchantId) {
        try {
            return jdbcTemplate.query(ENDPOINT_SQL,
                            (rs, row) -> new WebhookEndpoint(merchantId, rs.getString("webhook_url"),
                                    rs.getString("webhook_secret")),
                            merchantId)
                    .stream()
                    .findFirst();
        } catch (DataAccessException ex) {
            logMissingSchema(ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> findMerchantIdForUser(String email) {
        return queryForUuid(MERCHANT_FOR_USER_SQL, email);
    }

    private Optional<UUID> queryForUuid(String sql, Object argument) {
        try {
            return jdbcTemplate.query(sql, (rs, row) -> rs.getObject(1, UUID.class), argument)
                    .stream()
                    .filter(Objects::nonNull)
                    .findFirst();
        } catch (DataAccessException ex) {
            logMissingSchema(ex);
            return Optional.empty();
        }
    }

    private static void logMissingSchema(DataAccessException ex) {
        // Most likely the merchant module's tables are not in this database yet.
        log.debug("Merchant webhook lookup unavailable: {}", ex.getMostSpecificCause().getMessage());
    }
}
