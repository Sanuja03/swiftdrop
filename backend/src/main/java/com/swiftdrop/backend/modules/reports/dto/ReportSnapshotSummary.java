package com.swiftdrop.backend.modules.reports.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportSnapshotSummary(
        UUID id,
        Instant createdAt,
        LocalDate startDate,
        LocalDate endDate) {
}
