package com.swiftdrop.backend.modules.reports.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportSnapshotResponse(
        UUID id,
        UUID createdBy,
        Instant createdAt,
        LocalDate startDate,
        LocalDate endDate,
        ReportSnapshotData data) {
}
