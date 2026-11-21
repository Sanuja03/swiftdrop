package com.swiftdrop.backend.modules.reports.dto;

import java.time.LocalDate;

public record DailyDeliveryResponse(
        LocalDate deliveryDate,
        Long deliveries
) {
}