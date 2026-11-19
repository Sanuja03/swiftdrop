package com.swiftdrop.backend.modules.reports.dto;

import java.util.List;

public record ReportSnapshotData(
        ReportSummaryResponse summary,
        DeliverySuccessRateResponse successRate,
        List<DailyDeliveryResponse> dailyDeliveries,
        ReportPeriod successPeriod) {
    public ReportSnapshotData(ReportSummaryResponse summary, DeliverySuccessRateResponse successRate,
            List<DailyDeliveryResponse> dailyDeliveries) {
        this(summary, successRate, dailyDeliveries, null);
    }
}
