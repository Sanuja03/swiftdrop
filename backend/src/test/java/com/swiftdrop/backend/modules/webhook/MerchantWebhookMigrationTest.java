package com.swiftdrop.backend.modules.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class MerchantWebhookMigrationTest {
    @Test
    void consolidatedMerchantSchemaAndWebhookMigrationAgreeOnFreshDatabase() throws Exception {
        assertConsolidatedMerchantCompatibility(false);
    }

    @Test
    void consolidatedMerchantSchemaUpgradesLegacyWebhookRowsWithoutReplacingThem() throws Exception {
        assertConsolidatedMerchantCompatibility(true);
    }

    private void assertConsolidatedMerchantCompatibility(boolean legacyFirst) throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:merchant-merge-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        UUID user = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        var migration = new ResourceDatabasePopulator(new ClassPathResource("db/scripts/merchant_webhook_setup.sql"));
        try {
            jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY, email VARCHAR(255), role VARCHAR(50) DEFAULT 'ROLE_MERCHANT', status VARCHAR(50) DEFAULT 'ACTIVE', email_verified BOOLEAN DEFAULT TRUE)");
            jdbc.execute("CREATE TABLE parcels (id UUID PRIMARY KEY)");
            jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", user, "existing@shop.com");
            if (legacyFirst) {
                migration.execute(dataSource);
                jdbc.update("INSERT INTO merchants (id, user_id, webhook_url, webhook_secret) VALUES (?, ?, ?, ?)",
                        merchant, user, "https://hooks.shop.com/events", "existing-secret");
            }
            String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS merchants (");
            int end = schema.indexOf("-- Merchant webhook test pings", start);
            assertThat(start).isGreaterThanOrEqualTo(0);
            assertThat(end).isGreaterThan(start);
            // H2 rejects PostgreSQL's DEFAULT-after-PRIMARY-KEY order and timestamptz
            // alias. Normalize only equivalent type/default syntax, not upgrade SQL.
            // This exercises merge compatibility; it is not PostgreSQL validation.
            String merchantSql = schema.substring(start, end)
                    .replaceAll("UUID\\s+PRIMARY KEY\\s+DEFAULT gen_random_uuid\\(\\)", "UUID DEFAULT RANDOM_UUID() PRIMARY KEY")
                    .replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
            var merchantSchema = new ResourceDatabasePopulator(new ByteArrayResource(
                    merchantSql.getBytes(StandardCharsets.UTF_8)));
            merchantSchema.execute(dataSource);
            merchantSchema.execute(dataSource);
            if (legacyFirst) {
                assertThat(jdbc.queryForObject("SELECT webhook_secret FROM merchants WHERE id = ?", String.class, merchant))
                        .isEqualTo("existing-secret");
                assertThat(jdbc.queryForObject("SELECT business_name FROM merchants WHERE id = ?", String.class, merchant)).isNull();
                assertThat(jdbc.queryForObject("SELECT registration_status FROM merchants WHERE id = ?", String.class, merchant))
                        .isEqualTo("APPROVED");
            } else {
                jdbc.update("INSERT INTO merchants (id, user_id, business_name) VALUES (?, ?, ?)", merchant, user, "Existing Business");
            }
            String longSecret = "whsec_" + "x".repeat(180);
            jdbc.update("UPDATE merchants SET webhook_secret = ? WHERE id = ?", longSecret, merchant);
            migration.execute(dataSource);
            assertThat(jdbc.queryForObject("SELECT webhook_secret FROM merchants WHERE id = ?", String.class, merchant))
                    .isEqualTo(longSecret);
            assertThat(jdbc.queryForObject("SELECT registration_status FROM merchants WHERE id = ?", String.class, merchant))
                    .isEqualTo(legacyFirst ? "APPROVED" : "PENDING");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchants", Integer.class)).isEqualTo(1);
        } finally {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    @Test
    void migrationIsRepeatablePreservesDataAndEnforcesOwnershipLinks() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:migration-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        UUID user = UUID.randomUUID();
        UUID parcel = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        try {
            jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY, email VARCHAR(255))");
            jdbc.execute("CREATE TABLE parcels (id UUID PRIMARY KEY, tracking_number VARCHAR(32))");
            jdbc.update("INSERT INTO users VALUES (?, ?)", user, "existing@shop.com");
            jdbc.update("INSERT INTO parcels VALUES (?, ?)", parcel, "SDP1234567");
            var migration = new ResourceDatabasePopulator(new ClassPathResource("db/scripts/merchant_webhook_setup.sql"));
            migration.execute(dataSource);
            jdbc.update("INSERT INTO merchants (id, user_id, webhook_url, webhook_secret) VALUES (?, ?, ?, ?)",
                    merchant, user, "https://hooks.shop.com/events", "existing-secret");
            migration.execute(dataSource);

            assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user))
                    .isEqualTo("existing@shop.com");
            assertThat(jdbc.queryForObject("SELECT merchant_id FROM parcels WHERE id = ?", UUID.class, parcel)).isNull();
            assertThat(jdbc.queryForObject("SELECT webhook_secret FROM merchants WHERE id = ?", String.class, merchant))
                    .isEqualTo("existing-secret");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO merchants (id, user_id) VALUES (?, ?)", UUID.randomUUID(), user))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE parcels SET merchant_id = ? WHERE id = ?", UUID.randomUUID(), parcel))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("UPDATE parcels SET merchant_id = ? WHERE id = ?", merchant, parcel);
            assertThat(jdbc.queryForObject("SELECT merchant_id FROM parcels WHERE id = ?", UUID.class, parcel)).isEqualTo(merchant);
        } finally {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }
}
