package com.swiftdrop.backend.modules.zone.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param code short permanent identifier, e.g. NORTH_1; letters, digits and underscores
 *             (stored upper case, cannot be changed later)
 */
public record CreateZoneRequest(
        @NotBlank @Pattern(regexp = CODE_PATTERN) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal surcharge) {

    public static final String CODE_PATTERN = "^\\s*[A-Za-z0-9_]{2,40}\\s*$";
}
