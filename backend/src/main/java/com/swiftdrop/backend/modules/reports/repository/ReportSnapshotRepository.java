package com.swiftdrop.backend.modules.reports.repository;

import com.swiftdrop.backend.modules.reports.dto.ReportSnapshotData;
import com.swiftdrop.backend.modules.reports.dto.ReportSnapshotPage;
import com.swiftdrop.backend.modules.reports.dto.ReportSnapshotResponse;
import com.swiftdrop.backend.modules.reports.dto.ReportSnapshotSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.time.ZoneId;

@Repository
public class ReportSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    public ReportSnapshotRepository(JdbcTemplate jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<ReportSnapshotResponse> findById(UUID id) {
        return jdbc.query("SELECT * FROM public.report_snapshots WHERE id = ?", (row, index) ->
                new ReportSnapshotResponse(
                        row.getObject("id", UUID.class),
                        row.getObject("created_by", UUID.class),
                        row.getTimestamp("created_at").toInstant(),
                        row.getObject("start_date", LocalDate.class),
                        row.getObject("end_date", LocalDate.class),
                        mapper.readValue(row.getString("data_json"), ReportSnapshotData.class)), id)
                .stream().findFirst();
    }

    public void insert(ReportSnapshotResponse snapshot) {
        jdbc.update("""
                INSERT INTO public.report_snapshots
                    (id, created_by, created_at, start_date, end_date, data_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """, snapshot.id(), snapshot.createdBy(), Timestamp.from(snapshot.createdAt()),
                snapshot.startDate(), snapshot.endDate(), mapper.writeValueAsString(snapshot.data()));
    }

    public ReportSnapshotPage list(int page, int size) {
        return list(page, size, null, null);
    }

    public ReportSnapshotPage list(int page, int size, LocalDate startDate, LocalDate endDate) {
        String filter = startDate == null ? "" : " WHERE created_at >= ? AND created_at < ? ";
        var bounds = new java.util.ArrayList<Object>();
        if (startDate != null) {
            var zone = ZoneId.of("Asia/Colombo");
            bounds.add(Timestamp.from(startDate.atStartOfDay(zone).toInstant()));
            bounds.add(Timestamp.from(endDate.plusDays(1).atStartOfDay(zone).toInstant()));
        }
        var arguments = new java.util.ArrayList<>(bounds);
        arguments.add(size);
        arguments.add((long) page * size);
        var items = jdbc.query("""
                SELECT id, created_at, start_date, end_date FROM public.report_snapshots
                """ + filter + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", (row, index) -> new ReportSnapshotSummary(
                        row.getObject("id", UUID.class), row.getTimestamp("created_at").toInstant(),
                        row.getObject("start_date", LocalDate.class),
                        row.getObject("end_date", LocalDate.class)), arguments.toArray());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM public.report_snapshots" + filter, Long.class, bounds.toArray());
        return new ReportSnapshotPage(items, total, page, size);
    }
}
