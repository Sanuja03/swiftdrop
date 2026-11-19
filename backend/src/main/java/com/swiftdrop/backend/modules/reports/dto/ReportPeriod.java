package com.swiftdrop.backend.modules.reports.dto;

import java.time.LocalDate;
import java.util.Objects;

public record ReportPeriod(LocalDate startDate, LocalDate endDate) {
    public ReportPeriod {
        Objects.requireNonNull(startDate, "startDate is required");
        Objects.requireNonNull(endDate, "endDate is required");
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("Invalid report period");
    }
}
