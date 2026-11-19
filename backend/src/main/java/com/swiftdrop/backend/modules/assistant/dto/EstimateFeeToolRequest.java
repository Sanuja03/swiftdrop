package com.swiftdrop.backend.modules.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Natural-language parameters for fee estimation tool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimateFeeToolRequest {

    private String pickupCity;
    private String dropoffCity;
    private BigDecimal weightKg;
    private String serviceType;
    private String parcelSize;
}
