package com.swiftdrop.backend.modules.reports.service;

import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.reports.dto.*;
import com.swiftdrop.backend.modules.reports.repository.ReportSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportSnapshotServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final LocalDate start = LocalDate.of(2026, 11, 1);
    private final LocalDate end = LocalDate.of(2026, 11, 7);
    private final Instant savedAt = Instant.parse("2026-11-17T12:00:00Z");
    private final UserRepository users = mock(UserRepository.class);
    private final ReportService reports = mock(ReportService.class);
    private JdbcTemplate jdbc;
    private ReportSnapshotService snapshots;

    @BeforeEach
    void setUp() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:snapshots-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE public.users(id UUID PRIMARY KEY)");
        jdbc.update("INSERT INTO public.users VALUES (?)", userId);
        // H2 shares the table/index DDL. RLS is PostgreSQL-specific and reviewed in the SQL file.
        String sql = Files.readString(Path.of("database/report_snapshots.sql"));
        sql = sql.replace("ALTER TABLE public.report_snapshots ENABLE ROW LEVEL SECURITY;", "");
        try (var connection = datasource.getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        when(users.findByEmail("admin@example.test")).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(reports.getReportSummary()).thenReturn(new ReportSummaryResponse(5L, 3L, 1L, 0L, new BigDecimal("1000.00")));
        when(reports.getDeliverySuccessRate()).thenReturn(new DeliverySuccessRateResponse(3L, 1L, 75.0, 25.0));
        when(reports.getDailyDeliveries(start, end)).thenReturn(List.of(new DailyDeliveryResponse(end, 2L)));
        var repository = new ReportSnapshotRepository(jdbc, JsonMapper.builder().build());
        snapshots = new ReportSnapshotService(repository, reports, users, Clock.fixed(savedAt, ZoneOffset.UTC),
                new DataSourceTransactionManager(datasource));
    }

    @Test
    void persistsImmutableValuesAndRetriesWithoutDuplicating() {
        var request = new SaveReportSnapshotRequest(UUID.randomUUID(), start, end);
        var saved = snapshots.save(request, "ADMIN@example.test");
        when(reports.getReportSummary()).thenReturn(new ReportSummaryResponse(99L, 90L, 0L, 0L, BigDecimal.TEN));
        var reopened = snapshots.get(saved.id());
        assertEquals(5L, reopened.data().summary().totalParcels());
        assertEquals(savedAt, reopened.createdAt());
        assertEquals(end, reopened.data().dailyDeliveries().getFirst().deliveryDate());
        assertEquals(saved, snapshots.save(request, "admin@example.test"));
        assertEquals(1L, snapshots.list(0, 20).total());
        verify(reports, times(1)).getReportSummary();
    }

    @Test
    void rejectsReusingAnIdForAnotherPeriod() {
        UUID id = UUID.randomUUID();
        snapshots.save(new SaveReportSnapshotRequest(id, start, end), "admin@example.test");
        var error = assertThrows(ResponseStatusException.class, () -> snapshots.save(
                new SaveReportSnapshotRequest(id, start, end.plusDays(1)), "admin@example.test"));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(1L, snapshots.list(0, 20).total());
    }

    @Test
    void failedAggregationLeavesNoPartialSnapshot() {
        when(reports.getDailyDeliveries(any(), any())).thenThrow(new IllegalStateException("test failure"));
        assertThrows(IllegalStateException.class, () -> snapshots.save(
                new SaveReportSnapshotRequest(UUID.randomUUID(), start, end), "admin@example.test"));
        assertEquals(0L, snapshots.list(0, 20).total());
    }

    @Test
    void concurrentRetriesKeepOneSavedReport() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(reports.getReportSummary()).thenAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new ReportSummaryResponse(5L, 3L, 1L, 0L, BigDecimal.TEN);
        });
        var request = new SaveReportSnapshotRequest(UUID.randomUUID(), start, end);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> snapshots.save(request, "admin@example.test"));
            var second = executor.submit(() -> snapshots.save(request, "admin@example.test"));
            assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1L, snapshots.list(0, 20).total());
    }

    @Test
    void listIsPaginatedAndInvalidInputsAreRejected() {
        for (int index = 0; index < 3; index++) {
            snapshots.save(new SaveReportSnapshotRequest(UUID.randomUUID(), start, end), "admin@example.test");
        }
        assertEquals(2, snapshots.list(0, 2).items().size());
        assertEquals(1, snapshots.list(1, 2).items().size());
        assertThrows(ResponseStatusException.class, () -> snapshots.list(-1, 20));
        assertThrows(ResponseStatusException.class, () -> snapshots.list(0, 101));
        assertThrows(ResponseStatusException.class, () -> snapshots.get(UUID.randomUUID()));
        assertThrows(ResponseStatusException.class, () -> snapshots.save(
                new SaveReportSnapshotRequest(UUID.randomUUID(), end, start), "admin@example.test"));
        assertThrows(ResponseStatusException.class, () -> snapshots.save(
                new SaveReportSnapshotRequest(UUID.randomUUID(), start, end), "missing@example.test"));
    }

    @Test
    void savedDateFilterUsesSavedTimeAndFilteredPagination() {
        for (int index = 0; index < 3; index++) {
            snapshots.save(new SaveReportSnapshotRequest(UUID.randomUUID(), start, end), "admin@example.test");
        }
        var savedDay = LocalDate.of(2026, 11, 17);
        assertEquals(2, snapshots.list(0, 2, savedDay, savedDay).items().size());
        assertEquals(3L, snapshots.list(0, 2, savedDay, savedDay).total());
        assertEquals(1, snapshots.list(1, 2, savedDay, savedDay).items().size());
        assertEquals(0L, snapshots.list(0, 20, start, end).total());
        assertThrows(ResponseStatusException.class, () -> snapshots.list(0, 20, savedDay, null));
        assertThrows(ResponseStatusException.class, () -> snapshots.list(0, 20, end, start));
    }

    @Test
    void savedCopyPreservesIndependentOutcomePeriodAndProtectsRetryIdentity() {
        var period = new ReportPeriod(start.minusDays(23), end);
        when(reports.getDeliverySuccessRate(period.startDate(), period.endDate()))
                .thenReturn(new DeliverySuccessRateResponse(1L, 1L, 50.0, 50.0));
        var request = new SaveReportSnapshotRequest(UUID.randomUUID(), start, end, period);
        var saved = snapshots.save(request, "admin@example.test");
        assertEquals(period, snapshots.get(saved.id()).data().successPeriod());
        assertEquals(50.0, saved.data().successRate().successRate());
        assertEquals(saved, snapshots.save(request, "admin@example.test"));
        verify(reports, never()).getDeliverySuccessRate();
        assertThrows(ResponseStatusException.class, () -> snapshots.save(
                new SaveReportSnapshotRequest(request.id(), start, end), "admin@example.test"));
    }

    @Test
    void oldSavedJsonWithoutPeriodStillOpensAsOverallOutcomes() {
        var request = new SaveReportSnapshotRequest(UUID.randomUUID(), start, end);
        snapshots.save(request, "admin@example.test");
        String json = jdbc.queryForObject("SELECT data_json FROM report_snapshots WHERE id=?", String.class, request.id());
        json = json.replace(",\"successPeriod\":null", "");
        jdbc.update("UPDATE report_snapshots SET data_json=? WHERE id=?", json, request.id());
        assertNull(snapshots.get(request.id()).data().successPeriod());
        assertEquals(75.0, snapshots.get(request.id()).data().successRate().successRate());
    }
}
