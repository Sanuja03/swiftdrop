package com.swiftdrop.backend.modules.zone.dto;

import jakarta.validation.constraints.NotNull;

public record ZoneActiveRequest(@NotNull Boolean active) { }
