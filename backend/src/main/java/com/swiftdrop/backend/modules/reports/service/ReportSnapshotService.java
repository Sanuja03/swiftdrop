package com.swiftdrop.backend.modules.reports.service;

import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.reports.dto.*;
import com.swiftdrop.backend.modules.reports.repository.ReportSnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ReportSnapshotService {
    private final ReportSnapshotRepository snapshots;
    private final ReportService reports;
    private final UserRepository users;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ReportSnapshotService(ReportSnapshotRepository snapshots, ReportService reports,
            UserRepository users, Clock clock, PlatformTransactionManager transactionManager) {
        this.snapshots = snapshots;
        this.reports = reports;
        this.users = users;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        // All aggregates in a saved report see the same database state.
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public ReportSnapshotResponse save(SaveReportSnapshotRequest request, String email) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must not follow end date.");
        }
        UUID userId = users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."))
                .getId();
        try {
            return transaction.execute(status -> {
                var existing = snapshots.findById(request.id());
                if (existing.isPresent()) return matchingSnapshot(existing.get(), request, userId);

                var data = new ReportSnapshotData(reports.getReportSummary(),
                        request.successPeriod() == null ? reports.getDeliverySuccessRate()
                            : reports.getDeliverySuccessRate(request.successPeriod().startDate(), request.successPeriod().endDate()),
                        reports.getDailyDeliveries(request.startDate(), request.endDate()), request.successPeriod());
                var snapshot = new ReportSnapshotResponse(request.id(), userId, Instant.now(clock),
                        request.startDate(), request.endDate(), data);
                snapshots.insert(snapshot);
                return snapshot;
            });
        } catch (DataIntegrityViolationException error) {
            // A concurrent retry may have inserted this ID. Read after rollback.
            var existing = snapshots.findById(request.id());
            if (existing.isPresent()) return matchingSnapshot(existing.get(), request, userId);
            throw error;
        }
    }

    private ReportSnapshotResponse matchingSnapshot(ReportSnapshotResponse existing,
            SaveReportSnapshotRequest request, UUID userId) {
        if (!userId.equals(existing.createdBy()) || !request.startDate().equals(existing.startDate())
                || !request.endDate().equals(existing.endDate())
                || !Objects.equals(request.successPeriod(), existing.data().successPeriod())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Snapshot ID is already in use.");
        }
        return existing;
    }

    public ReportSnapshotResponse get(UUID id) {
        return snapshots.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved report not found."));
    }

    public ReportSnapshotPage list(int page, int size) {
        return list(page, size, null, null);
    }

    public ReportSnapshotPage list(int page, int size, LocalDate startDate, LocalDate endDate) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report page.");
        }
        if ((startDate == null) != (endDate == null) || (startDate != null && startDate.isAfter(endDate))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid saved-date range.");
        }
        return startDate == null ? snapshots.list(page, size) : snapshots.list(page, size, startDate, endDate);
    }
}
