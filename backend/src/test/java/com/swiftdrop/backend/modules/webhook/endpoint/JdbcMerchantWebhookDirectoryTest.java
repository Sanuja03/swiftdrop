package com.swiftdrop.backend.modules.webhook.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Runs the real SQL against an in-memory database shaped like the merchant module's tables.
 */
class JdbcMerchantWebhookDirectoryTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID NO_HOOK_MERCHANT_ID = UUID.randomUUID();
    private static final UUID MERCHANT_PARCEL = UUID.randomUUID();
    private static final UUID CUSTOMER_PARCEL = UUID.randomUUID();

    private JdbcTemplate jdbcTemplate;
    private JdbcMerchantWebhookDirectory directory;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:directory-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        directory = new JdbcMerchantWebhookDirectory(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SHUTDOWN");
    }

    @Test
    void findsMerchantEndpointAndOwnerFromMerchantTables() {
        createMerchantSchema();

        assertThat(directory.findMerchantIdForParcel(MERCHANT_PARCEL)).contains(MERCHANT_ID);
        assertThat(directory.findEndpoint(MERCHANT_ID))
                .contains(new WebhookEndpoint(MERCHANT_ID, "https://shop.example.lk/hook", "whsec_1"));
        assertThat(directory.findMerchantIdForUser("orders@kandy-crafts.lk")).contains(MERCHANT_ID);
    }

    @Test
    void customerParcelAndUnknownValuesFindNothing() {
        createMerchantSchema();

        assertThat(directory.findMerchantIdForParcel(CUSTOMER_PARCEL)).isEmpty();
        assertThat(directory.findMerchantIdForParcel(UUID.randomUUID())).isEmpty();
        assertThat(directory.findMerchantIdForUser("someone@else.lk")).isEmpty();
    }

    @Test
    void merchantWithoutUrlOrSecretHasNoEndpoint() {
        createMerchantSchema();

        assertThat(directory.findEndpoint(NO_HOOK_MERCHANT_ID)).isEmpty();
    }

    @Test
    void missingMerchantTablesMeanNoWebhooksInsteadOfErrors() {
        jdbcTemplate.execute("CREATE TABLE parcels (id UUID PRIMARY KEY)");

        assertThat(directory.findMerchantIdForParcel(MERCHANT_PARCEL)).isEmpty();
        assertThat(directory.findEndpoint(MERCHANT_ID)).isEmpty();
        assertThat(directory.findMerchantIdForUser("orders@kandy-crafts.lk")).isEmpty();
    }

    @Test
    void secretIsNeverPrinted() {
        assertThat(new WebhookEndpoint(MERCHANT_ID, "https://x", "whsec_super_secret").toString())
                .doesNotContain("whsec_super_secret")
                .contains("***");
    }

    private void createMerchantSchema() {
        jdbcTemplate.execute("CREATE TABLE users (id UUID PRIMARY KEY, email VARCHAR(255))");
        jdbcTemplate.execute("""
                CREATE TABLE merchants (id UUID PRIMARY KEY, user_id UUID,
                    webhook_url VARCHAR(500), webhook_secret VARCHAR(128))
                """);
        jdbcTemplate.execute("CREATE TABLE parcels (id UUID PRIMARY KEY, merchant_id UUID)");

        jdbcTemplate.update("INSERT INTO users VALUES (?, ?)", USER_ID, "orders@kandy-crafts.lk");
        jdbcTemplate.update("INSERT INTO merchants VALUES (?, ?, ?, ?)",
                MERCHANT_ID, USER_ID, "https://shop.example.lk/hook", "whsec_1");
        jdbcTemplate.update("INSERT INTO merchants VALUES (?, ?, ?, ?)", NO_HOOK_MERCHANT_ID, null, "", null);
        jdbcTemplate.update("INSERT INTO parcels VALUES (?, ?)", MERCHANT_PARCEL, MERCHANT_ID);
        jdbcTemplate.update("INSERT INTO parcels VALUES (?, ?)", CUSTOMER_PARCEL, null);
    }
}
