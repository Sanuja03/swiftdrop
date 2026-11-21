
package com.swiftdrop.backend.modules.notifications.dto;

import com.swiftdrop.backend.modules.notifications.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID parcelId,
        String title,
        String message,
        String notificationType,
        boolean isRead,
        Instant createdAt,
        Instant readAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getParcelId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
