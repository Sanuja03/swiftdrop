package com.swiftdrop.backend.modules.webhook.endpoint;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.swiftdrop.backend.modules.webhook.exception.WebhookException;
import com.swiftdrop.backend.modules.webhook.validation.WebhookUrlValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the shared merchant mapping with plain SQL. Uses parcels.merchant_id,
 * merchants.user_id, merchants.webhook_url and merchants.webhook_secret.
 *
 * <p>If those tables or columns do not exist yet, every lookup returns empty, so parcels
 * simply produce no webhooks instead of failing.
 *
 * <p>WebhookConfigurationService writes these same columns. Destinations are checked again
 * on every lookup, including retries and configurations saved outside the API.
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
    private final WebhookUrlValidator urlValidator;

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
                    .findFirst()
                    .filter(endpoint -> isAllowed(endpoint.url()));
        } catch (DataAccessException ex) {
            logMissingSchema(ex);
            return Optional.empty();
        }
    }

    private boolean isAllowed(String url) {
        try {
            urlValidator.validate(url);
            return true;
        } catch (WebhookException ex) {
            log.warn("Merchant webhook destination rejected by public-address policy");
            return false;
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
