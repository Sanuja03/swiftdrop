
package com.swiftdrop.backend.modules.notifications.service;

import com.swiftdrop.backend.modules.notifications.dto.NotificationResponse;
import com.swiftdrop.backend.modules.notifications.entity.Notification;
import com.swiftdrop.backend.modules.notifications.repository.NotificationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Creates an in-app notification for a specific user.
     *
     * Parcel-event notifications use createEventNotification to retain their event ID.
     */
    @Transactional
    public NotificationResponse createNotification(
            UUID userId,
            UUID parcelId,
            String title,
            String message,
            String notificationType
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("Notification user ID is required");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title is required");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .parcelId(parcelId)
                .title(title)
                .message(message)
                .notificationType(
                        notificationType == null || notificationType.isBlank()
                                ? "GENERAL"
                                : notificationType
                )
                .build();

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(UUID userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification not found"
                ));

        notification.markAsRead();

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    /**
     * Creates a notification associated with a RabbitMQ domain event.
     * The database's unique event_id constraint prevents duplicate records.
     */
    @Transactional
    public NotificationResponse createEventNotification(
            UUID eventId,
            UUID userId,
            UUID parcelId,
            String title,
            String message,
            String notificationType
    ) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (userId == null) {
            throw new IllegalArgumentException("Notification user ID is required");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title is required");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        Notification notification = Notification.builder()
                .eventId(eventId)
                .userId(userId)
                .parcelId(parcelId)
                .title(title)
                .message(message)
                .notificationType(notificationType)
                .build();

        return NotificationResponse.from(
                notificationRepository.save(notification)
        );
    }
}
