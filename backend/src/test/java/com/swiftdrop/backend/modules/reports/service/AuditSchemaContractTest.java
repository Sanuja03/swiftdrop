package com.swiftdrop.backend.modules.reports.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.swiftdrop.backend.modules.notifications.entity.Notification;
import com.swiftdrop.backend.modules.reports.dto.*;
import com.swiftdrop.backend.modules.reports.repository.ReportSnapshotRepository;
import tools.jackson.databind.json.JsonMapper;

/** Checks the actual audit DDL, rather than a hand-written substitute for these two tables.
 * H2 adaptations below do not verify PostgreSQL RLS or partial indexes; those need PostgreSQL.
 */
class AuditSchemaContractTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void schema() throws IOException {
        jdbc = database();
        jdbc.execute(table(audit(), "notifications"));
        jdbc.execute(table(audit(), "report_snapshots"));
    }

    private JdbcTemplate database() {
        var db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:audit-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
        db.execute("CREATE TABLE parcels (id UUID PRIMARY KEY)");
        return db;
    }

    private String audit() throws IOException {
        return new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    private String table(String sql, String name) {
        var match = Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS (?:public\\.)?" + name
                + "\\s*\\(.*?;").matcher(sql);
        assertTrue(match.find(), "Missing audit table " + name);
        return match.group().replaceAll("(?i)TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE")
                .replaceAll("(?i)gen_random_uuid\\(\\)", "RANDOM_UUID()")
                // H2 requires the default before the primary-key constraint.
                .replaceAll("(?i)PRIMARY KEY DEFAULT RANDOM_UUID\\(\\)", "DEFAULT RANDOM_UUID() PRIMARY KEY");
    }

    private List<java.util.Map<String, Object>> columns(JdbcTemplate db, String table) {
        return db.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """, table);
    }

    @Test
    void consolidatedTablesMatchOriginalModuleScriptsAndDevReportTable() throws IOException {
        var original = database();
        original.execute(table(Files.readString(Path.of("database/notifications.sql")), "notifications"));
        original.execute(table(Files.readString(Path.of("database/report_snapshots.sql")), "report_snapshots"));
        assertEquals(columns(original, "notifications"), columns(jdbc, "notifications"));
        assertEquals(columns(original, "report_snapshots"), columns(jdbc, "report_snapshots"));
        var dev = database();
        dev.execute(table(new ClassPathResource("db/dev/h2-jdbc-tables.sql")
                .getContentAsString(StandardCharsets.UTF_8), "report_snapshots"));
        assertEquals(columns(dev, "report_snapshots"), columns(jdbc, "report_snapshots"));
    }

    @Test
    void notificationEntityColumnsDefaultsDuplicateEventsAndForeignKeysMatchAudit() {
        var expected = java.util.Arrays.stream(Notification.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(f -> {
                    var column = f.getAnnotation(jakarta.persistence.Column.class);
                    return column != null && !column.name().isBlank() ? column.name() : f.getName();
                }).collect(java.util.stream.Collectors.toSet());
        var actual = columns(jdbc, "notifications").stream().map(c -> c.get("column_name"))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual);
        UUID user = UUID.randomUUID(), parcel = UUID.randomUUID(), event = UUID.randomUUID();
        jdbc.update("INSERT INTO users VALUES (?)", user);
        jdbc.update("INSERT INTO parcels VALUES (?)", parcel);
        jdbc.update("INSERT INTO notifications (user_id, parcel_id, event_id, title, message) VALUES (?, ?, ?, ?, ?)",
                user, parcel, event, "Audit test", "Synthetic notification");
        assertEquals(false, jdbc.queryForObject("SELECT is_read FROM notifications", Boolean.class));
        assertEquals("PARCEL_STATUS", jdbc.queryForObject("SELECT notification_type FROM notifications", String.class));
        assertNotNull(jdbc.queryForObject("SELECT created_at FROM notifications", java.sql.Timestamp.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO notifications (user_id, event_id, title, message) VALUES (?, ?, 'Duplicate', 'Test')", user, event));
        jdbc.update("DELETE FROM parcels WHERE id = ?", parcel);
        assertNull(jdbc.queryForObject("SELECT parcel_id FROM notifications", UUID.class));
        jdbc.update("DELETE FROM users WHERE id = ?", user);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
    }

    @Test
    void snapshotRepositoryRoundTripsAgainstAuditDdlAndRejectsInvalidDateOrder() {
        var repository = new ReportSnapshotRepository(jdbc, JsonMapper.builder().build());
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO users VALUES (?)", user);
        var day = LocalDate.of(2026, 11, 21);
        var data = new ReportSnapshotData(new ReportSummaryResponse(3L, 0L, 1L, 1L,
                new java.math.BigDecimal("2970.00"), 0L),
                new DeliverySuccessRateResponse(0L, 0L, 0.0, 0.0), List.of());
        var snapshot = new ReportSnapshotResponse(UUID.randomUUID(), user,
                Instant.parse("2026-11-21T00:00:00Z"), day, day, data);
        repository.insert(snapshot);
        assertEquals(snapshot, repository.findById(snapshot.id()).orElseThrow());
        assertEquals(1L, repository.list(0, 10, day, day).total());
        assertThrows(DataIntegrityViolationException.class, () -> repository.insert(new ReportSnapshotResponse(
                UUID.randomUUID(), user, snapshot.createdAt(), day.plusDays(1), day, data)));
        jdbc.update("DELETE FROM users WHERE id = ?", user);
        assertNull(repository.findById(snapshot.id()).orElseThrow().createdBy());
    }
}
