package com.swiftdrop.backend.modules.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Structured fee estimate result, providing breakdown or missing field clarification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimateFeeToolResult {

    private boolean success;
    private BigDecimal baseFee;
    private BigDecimal weightCharge;
    private BigDecimal zoneCharge;
    private BigDecimal totalFee;
    private String currency;
    private Instant estimatedDeliveryAt;
    private String pickupZone;
    private String dropoffZone;
    private String serviceType;
    private String parcelSize;
    private String message;
    private List<String> missingFields;
    private List<String> availableZones;
}
