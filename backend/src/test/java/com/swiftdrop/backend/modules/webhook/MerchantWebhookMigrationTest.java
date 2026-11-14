package com.swiftdrop.backend.modules.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class MerchantWebhookMigrationTest {
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
