package com.swiftdrop.backend.modules.reports.dto;

import java.math.BigDecimal;

public record ReportSummaryResponse(
        Long totalParcels,
        Long deliveredParcels,
        Long inTransitParcels,
        Long outForDeliveryParcels,
        BigDecimal totalFees
) {
}