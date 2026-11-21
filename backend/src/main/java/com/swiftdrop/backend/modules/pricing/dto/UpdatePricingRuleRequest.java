package com.swiftdrop.backend.modules.pricing.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** New fees (LKR, two decimals, never negative) and whether the service can be booked. */
public record UpdatePricingRuleRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal baseFee,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal perKgFee,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal interZoneFee,
        @NotNull Boolean active) { }
