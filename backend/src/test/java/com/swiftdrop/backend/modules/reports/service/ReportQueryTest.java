package com.swiftdrop.backend.modules.reports.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportQueryTest {
    private JdbcTemplate jdbc;
    private ReportService reports;

    @BeforeEach
    void setUp() {
        // H2 keeps AT TIME ZONE results zoned and casts them to DATE in the session
        // zone. PostgreSQL returns a local timestamp here. Initialize every H2
        // connection explicitly so these Colombo-boundary checks also work on UTC CI.
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:report-query-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=SET TIME ZONE 'Asia/Colombo'", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE parcels (id UUID PRIMARY KEY, status VARCHAR(30), fee NUMERIC(12,2))");
        jdbc.execute("CREATE TABLE parcel_status_history (parcel_id UUID, to_status VARCHAR(30), changed_at TIMESTAMP WITH TIME ZONE)");
        reports = new ReportService(jdbc);
    }

    @Test
    void emptyDatabaseProducesZeroSummaryAndRates() {
        var summary = reports.getReportSummary();
        assertEquals(0L, reports.getTotalParcels());
        assertEquals(0L, summary.totalParcels());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalFees()));
        assertEquals(0.0, reports.getDeliverySuccessRate().successRate());
        assertEquals(0.0, reports.getDeliverySuccessRate().failureRate());
    }

    @Test
    void summaryAndRatesAggregateActualRows() {
        for (String status : new String[]{"DELIVERED", "DELIVERED", "FAILED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "CREATED"}) {
            jdbc.update("INSERT INTO parcels VALUES (?, ?, ?)", UUID.randomUUID(), status, new BigDecimal("100.50"));
        }
        var summary = reports.getReportSummary();
        assertEquals(6L, reports.getTotalParcels());
        assertEquals(6L, summary.totalParcels());
        assertEquals(2L, summary.deliveredParcels());
        assertEquals(1L, summary.inTransitParcels());
        assertEquals(1L, summary.outForDeliveryParcels());
        assertEquals(0, new BigDecimal("603.00").compareTo(summary.totalFees()));
        var rates = reports.getDeliverySuccessRate();
        assertEquals(2L, rates.deliveredParcels());
        assertEquals(1L, rates.failedParcels());
        assertEquals(66.6666667, rates.successRate(), 0.000001);
        assertEquals(33.3333333, rates.failureRate(), 0.000001);
    }

    @Test
    void dailyCountsUseSriLankanDatesInclusiveBoundsAndDistinctParcels() {
        UUID first = UUID.randomUUID();
        history(first, "DELIVERED", "2026-11-16T18:29:59Z"); // 16 Nov 23:59:59 in Colombo
        history(first, "DELIVERED", "2026-11-16T18:30:00Z"); // 17 Nov 00:00
        history(first, "DELIVERED", "2026-11-17T02:00:00Z"); // repeated row on the same day
        history(UUID.randomUUID(), "DELIVERED", "2026-11-17T18:29:59Z");
        history(UUID.randomUUID(), "DELIVERED", "2026-11-17T18:30:00Z"); // 18 Nov, excluded
        history(UUID.randomUUID(), "FAILED", "2026-11-17T10:00:00Z");
        var day = LocalDate.of(2026, 11, 17);
        var result = reports.getDailyDeliveries(day, day);
        assertEquals(1, result.size());
        assertEquals(day, result.getFirst().deliveryDate());
        assertEquals(2L, result.getFirst().deliveries());
    }

    private void history(UUID parcel, String status, String instant) {
        jdbc.update("INSERT INTO parcel_status_history VALUES (?, ?, CAST(? AS TIMESTAMP WITH TIME ZONE))",
                parcel, status, instant);
    }

    @Test
    void outcomePeriodUsesLatestOutcomeSriLankanDatesAndDoesNotChangeOverview() {
        UUID delivered = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        UUID movedOutside = UUID.randomUUID();
        for (UUID id : new UUID[]{delivered, outside, movedOutside}) {
            jdbc.update("INSERT INTO parcels VALUES (?, 'DELIVERED', 100)", id);
        }
        jdbc.update("INSERT INTO parcels VALUES (?, 'FAILED', 100)", failed);
        history(delivered, "FAILED", "2026-11-15T00:00:00Z");
        history(delivered, "DELIVERED", "2026-11-16T18:30:00Z"); // 17 Nov midnight
        history(delivered, "DELIVERED", "2026-11-17T01:00:00Z"); // duplicate outcome
        history(failed, "FAILED", "2026-11-17T18:29:59Z");
        history(outside, "DELIVERED", "2026-11-16T18:29:59Z");
        history(movedOutside, "FAILED", "2026-11-17T01:00:00Z");
        history(movedOutside, "DELIVERED", "2026-11-17T18:30:00Z"); // 18 Nov
        var day = LocalDate.of(2026, 11, 17);
        var rate = reports.getDeliverySuccessRate(day, day);
        assertEquals(1L, rate.deliveredParcels());
        assertEquals(1L, rate.failedParcels());
        assertEquals(50.0, rate.successRate());
        assertEquals(4L, reports.getReportSummary().totalParcels());
        assertEquals(1L, reports.getReportSummary().failedParcels());
        assertEquals(3L, reports.getDeliverySuccessRate().deliveredParcels());
    }
}
