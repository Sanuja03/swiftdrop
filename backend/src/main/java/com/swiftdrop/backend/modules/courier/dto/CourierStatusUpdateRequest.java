package com.swiftdrop.backend.modules.courier.dto;

import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourierStatusUpdateRequest(@NotNull ParcelStatus status, @Size(max = 500) String note) {
}
