package com.swiftdrop.backend.modules.zone.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Name and surcharge are editable; the code is permanent. */
public record UpdateZoneRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal surcharge) { }
