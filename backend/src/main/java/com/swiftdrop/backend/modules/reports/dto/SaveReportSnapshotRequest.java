package com.swiftdrop.backend.modules.reports.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record SaveReportSnapshotRequest(
        @NotNull UUID id,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        ReportPeriod successPeriod) {
    public SaveReportSnapshotRequest(UUID id, LocalDate startDate, LocalDate endDate) {
        this(id, startDate, endDate, null);
    }
}
