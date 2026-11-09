package com.swiftdrop.backend.modules.reports.dto;

public record DeliverySuccessRateResponse(
    Long deliveredParcels,
    Long failedParcels,
    Double successRate,
    Double failureRate
) {}
