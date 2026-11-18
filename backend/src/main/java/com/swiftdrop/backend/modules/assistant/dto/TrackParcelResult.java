package com.swiftdrop.backend.modules.assistant.dto;

import com.swiftdrop.backend.modules.tracking.dto.PublicTrackingResponse;
import com.swiftdrop.backend.modules.tracking.dto.TrackingEventResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Structured parcel tracking result for assistant queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackParcelResult {

    private boolean found;
    private String trackingNumber;
    private String status;
    private String statusLabel;
    private String originCity;
    private String destinationCity;
    private String serviceType;
    private String serviceLabel;
    private Instant estimatedDeliveryAt;
    private Instant lastUpdatedAt;
    private List<TrackingEventResponse> timeline;
    private String message;

    public static TrackParcelResult fromResponse(PublicTrackingResponse response) {
        return TrackParcelResult.builder()
                .found(true)
                .trackingNumber(response.trackingNumber())
                .status(response.status() != null ? response.status().name() : null)
                .statusLabel(response.statusLabel())
                .originCity(response.originCity())
                .destinationCity(response.destinationCity())
                .serviceType(response.serviceType() != null ? response.serviceType().name() : null)
                .serviceLabel(response.serviceLabel())
                .estimatedDeliveryAt(response.estimatedDeliveryAt())
                .lastUpdatedAt(response.lastUpdatedAt())
                .timeline(response.timeline())
                .message("Parcel " + response.trackingNumber() + " is currently " + response.statusLabel() + ".")
                .build();
    }

    public static TrackParcelResult notFound(String trackingNumber) {
        return TrackParcelResult.builder()
                .found(false)
                .trackingNumber(trackingNumber)
                .message("No parcel found matching tracking number " + trackingNumber + ".")
                .build();
    }

    public static TrackParcelResult failure(String trackingNumber, String message) {
        return TrackParcelResult.builder()
                .found(false)
                .trackingNumber(trackingNumber)
                .message(message)
                .build();
    }
}
