package com.swiftdrop.backend.modules.reports.dto;

import java.util.List;

public record ReportSnapshotPage(
        List<ReportSnapshotSummary> items,
        long total,
        int page,
        int size) {
}
